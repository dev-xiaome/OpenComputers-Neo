package li.cil.oc.server.fs

import java.io
import java.nio.file.Paths
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.UUID
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.api
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.server.component
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.util.Try

object FileSystem extends api.detail.FileSystemAPI {
  lazy val isCaseInsensitive: Boolean = Settings.get.forceCaseInsensitive || (try {
    val uuid = UUID.randomUUID().toString
    val saveDir = ServerLifecycleHooks.getCurrentServer.getWorldPath(LevelResource.ROOT).toFile
    val lowerCase = new io.File(saveDir, uuid + "oc_rox")
    val upperCase = new io.File(saveDir, uuid + "OC_ROX")
    // This should NEVER happen but could also lead to VERY weird bugs, so we
    // make sure the files don't exist.
    lowerCase.exists() && lowerCase.delete()
    upperCase.exists() && upperCase.delete()
    lowerCase.createNewFile()
    val insensitive = upperCase.exists()
    lowerCase.delete()
    insensitive
  }
  catch {
    case t: Throwable =>
      // Among the security errors, createNewFile can throw an IOException.
      // We just fall back to assuming case insensitive, since that's always
      // safe in those cases.
      OpenComputers.log.warn("Couldn't determine if file system is case sensitive, falling back to insensitive.", t)
      true
  })

  // Worst-case: we're on Windows or using a FAT32 partition mounted in *nix.
  // Note: we allow / as the path separator and expect all \s to be converted
  // accordingly before the path is passed to the file system.
  private val invalidChars = """\:*?"<>|""".toSet

  def isValidFilename(name: String): Boolean = !name.exists(invalidChars.contains)

  def validatePath(path: String): String = {
    if (!isValidFilename(path)) {
      throw new java.io.IOException("path contains invalid characters")
    }
    path
  }

  override def fromResource(loc: ResourceLocation): api.fs.FileSystem = {
    val innerPath = "assets/" + loc.getNamespace + "/" + (loc.getPath.trim + "/")

    // ModDevGradle exposes compiled classes and resources as separate roots.
    // The mod file points at build/classes/java/main, so looking relative to
    // that directory cannot find assets in build/resources/main. Resolve a
    // real classpath directory first; packaged mods fall through to the JAR
    // handling below.
    Option(System.getProperty("fml.modFolders")).iterator.
      flatMap(_.split(java.util.regex.Pattern.quote(io.File.pathSeparator)).iterator).
      flatMap { entry =>
        entry.split("%%", 2) match {
          case Array(namespace, root) if namespace.split(",").contains(loc.getNamespace) =>
            Some(new io.File(root, innerPath))
          case _ => None
        }
      }.
      find(file => file.exists() && file.isDirectory) match {
      case Some(directory) => return new ReadOnlyFileSystem(directory)
      case _ =>
    }

    val loaders = Seq(getClass.getClassLoader, Thread.currentThread.getContextClassLoader).filter(_ != null).distinct
    loaders.iterator.
      flatMap(loader => Option(loader.getResource(innerPath))).
      filter(_.getProtocol == "file").
      flatMap(url => Try(Paths.get(url.toURI).toFile).toOption).
      find(file => file.exists() && file.isDirectory) match {
      case Some(directory) => return new ReadOnlyFileSystem(directory)
      case _ =>
    }

    val modInfo = FMLLoader.getLoadingModList().getModFileById(loc.getNamespace)
    val file = modInfo.getFile().getFilePath().toFile()

    if (!file.exists) return null
    if (!file.isDirectory) {
      ZipFileInputStreamFileSystem.fromFile(file, innerPath)
    }
    else {
      new io.File(file, innerPath) match {
        case fsp if fsp.exists() && fsp.isDirectory =>
          new ReadOnlyFileSystem(fsp)
        case _ => null
      }
    }
  }

  def fromResource(manager: ResourceManager, loc: ResourceLocation): api.fs.FileSystem =
    ResourceManagerFileSystem.fromResource(manager, loc)

  def readResource(manager: ResourceManager, loc: ResourceLocation): Option[Array[Byte]] =
    ResourceManagerFileSystem.readResource(manager, loc)

  override def fromSaveDirectory(root: String, capacity: Long, buffered: Boolean): Capacity = {
    val path = ServerLifecycleHooks.getCurrentServer.getWorldPath(new LevelResource(Settings.savePath + root)).toFile
    if (!path.isDirectory) {
      path.delete()
    }
    path.mkdirs()
    if (path.exists() && path.isDirectory) {
      if (buffered) new BufferedFileSystem(path, capacity)
      else new ReadWriteFileSystem(path, capacity)
    }
    else null
  }

  def removeAddress(fsStack: ItemStack): Boolean = {
    fsStack.getItem match {
      case drive: FileSystemLike => {
        val data = li.cil.oc.integration.opencomputers.Item.dataTag(fsStack)
        if (data.contains("node")) {
          val nodeData = data.getCompound("node")
          if (nodeData.contains("address")) {
            nodeData.remove("address")
            return true
          }
        }
      }
      case _ =>
    }
    false
  }

  def fromMemory(capacity: Long): api.fs.FileSystem = new RamFileSystem(capacity)

  override def asReadOnly(fileSystem: api.fs.FileSystem): api.fs.FileSystem =
    if (fileSystem.isReadOnly) fileSystem
    else {
      new ReadOnlyWrapper(fileSystem)
    }

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: Label, host: EnvironmentHost, accessSound: String, speed: Int) =
    asManagedEnvironment(fileSystem, label, host, accessSound, speed, Settings.get.hddReadCost, Settings.get.hddWriteCost)

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: Label, host: EnvironmentHost, accessSound: String, speed: Int,
                           readEnergyCost: Double, writeEnergyCost: Double) =
    Option(fileSystem).map(fs => new component.FileSystem(fs, label, Option(host), Option(accessSound),
      (speed - 1) max 0 min 5, readEnergyCost max 0, writeEnergyCost max 0)).orNull

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: String, host: EnvironmentHost, accessSound: String, speed: Int) =
    asManagedEnvironment(fileSystem, new ReadOnlyLabel(label), host, accessSound, speed)

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: Label, host: EnvironmentHost, sound: String) =
    asManagedEnvironment(fileSystem, label, host, sound, 1)

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: String, host: EnvironmentHost, sound: String) =
    asManagedEnvironment(fileSystem, new ReadOnlyLabel(label), host, sound, 1)

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: Label) =
    asManagedEnvironment(fileSystem, label, null, null, 1)

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: String) =
    asManagedEnvironment(fileSystem, new ReadOnlyLabel(label), null, null, 1)

  def asManagedEnvironment(fileSystem: api.fs.FileSystem) =
    asManagedEnvironment(fileSystem, null: Label, null, null, 1)

  abstract class ItemLabel(val stack: ItemStack) extends Label

  class ReadOnlyLabel(private var label: String) extends Label {
    def setLabel(value: String) = throw new IllegalArgumentException("label is read only")

    def getLabel(provider: HolderLookup.Provider): String = label

    override def loadData(holder: DataComponentHolder): Unit = {
      for (value <- holder.getComponent(OCComponents.LABEL)) {
        label = value
      }
    }

    override def saveData(holder: MutableDataComponentHolder): Unit = {
      if(label != null) {
        holder.setComponent(OCComponents.LABEL, label)
      }
    }
  }

  private class ReadOnlyFileSystem(protected val root: io.File)
    extends InputStreamFileSystem
    with FileInputStreamFileSystem

  private class ReadWriteFileSystem(protected val root: io.File, protected val capacity: Long)
    extends OutputStreamFileSystem
    with FileOutputStreamFileSystem
    with Capacity

  private class RamFileSystem(protected val capacity: Long)
    extends VirtualFileSystem
    with Volatile
    with Capacity

  private class BufferedFileSystem(protected val fileRoot: io.File, protected val capacity: Long)
    extends VirtualFileSystem
    with Buffered
    with Capacity {
    protected override def segments(path: String): Array[String] = {
      val parts = super.segments(path)
      if (isCaseInsensitive) toCaseInsensitive(parts) else parts
    }

    private def toCaseInsensitive(path: Array[String]): Array[String] = {
      var node = root
      path.map(segment => {
        assert(node != null, "corrupted virtual file system")
        node.children.find(entry => entry._1.toLowerCase == segment.toLowerCase) match {
          case Some((name, child: VirtualDirectory)) =>
            node = child
            name
          case Some((name, child: VirtualFile)) =>
            node = null
            name
          case _ => segment
        }
      })
    }
  }

}
