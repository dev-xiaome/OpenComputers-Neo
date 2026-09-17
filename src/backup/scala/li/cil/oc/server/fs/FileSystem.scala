package li.cil.oc.server.fs

import java.io
import java.net.JarURLConnection
import java.nio.file.Paths
import java.util.UUID

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.server.component
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.util.Try

object FileSystem extends api.detail.FileSystemAPI {

  lazy val isCaseInsensitive: Boolean = Settings.get.forceCaseInsensitive || (try {
    val uuid = UUID.randomUUID().toString
    // 1.21.1：NeoForge 移除了 `DimensionManager`，存档根目录改为向当前服务端查询。
    // 无服务端上下文时这里会抛异常，由下面的 catch 回退为「大小写不敏感」。
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

  override def fromClass(clazz: Class[_], domain: String, root: String): api.fs.FileSystem = {
    // 1.21.1 适配：本项目的 API 签名沿用 1.7.10 的 `fromClass(clazz, domain, root)`，
    // 而 1.20 CE / 1.21.1 上游把它换成了 `fromResource(ResourceLocation)`。
    // 这里保留旧签名，路径解析采用上游的做法，依次尝试：
    // 开发环境的 ModDevGradle 输出根、类加载器资源、最后是 FML 的模组文件（打包后即模组 jar）。
    val innerPath = "assets/" + domain + "/" + (root.trim.stripPrefix("/").stripSuffix("/") + "/")

    // 路径一：ModDevGradle 开发环境。编译产物与资源输出在不同的根目录下，
    // 只有资源根底下才有 assets，因此借助系统属性 `fml.modFolders`
    // （形如 `<模组id>%%<绝对路径>`，多项以路径分隔符连接）逐个尝试。
    val modFolderRoots = Option(System.getProperty("fml.modFolders")).toSeq
      .flatMap(_.split(java.util.regex.Pattern.quote(io.File.pathSeparator)))
      .flatMap { entry =>
        entry.split("%%", 2) match {
          case Array(namespace, folderRoot) if namespace.split(",").contains(domain) =>
            Some(new io.File(folderRoot, innerPath))
          case _ => None
        }
      }
    modFolderRoots.find(file => file.exists() && file.isDirectory) match {
      case Some(directory) => return new ReadOnlyFileSystem(directory)
      case _ =>
    }

    // 路径二：类加载器资源查找。开发环境与「模组 jar 位于普通类路径」两种情形都适用。
    val loaders = Seq(Option(clazz.getClassLoader), Option(Thread.currentThread.getContextClassLoader)).flatten.distinct
    val resourceUrls = loaders.flatMap(loader => Option(loader.getResource(innerPath)))

    // 资源落在目录里，直接包成只读文件系统。
    resourceUrls.filter(_.getProtocol == "file")
      .flatMap(url => Try(Paths.get(url.toURI).toFile).toOption)
      .find(file => file.exists() && file.isDirectory) match {
      case Some(directory) => return new ReadOnlyFileSystem(directory)
      case _ =>
    }

    // 资源落在归档里，先用 JarURLConnection 反查归档文件，再走 ZIP 文件系统。
    resourceUrls.filter(_.getProtocol == "jar")
      .flatMap(url => Try(url.openConnection().asInstanceOf[JarURLConnection].getJarFileURL.toURI).toOption)
      .map(uri => new io.File(uri))
      .find(file => file.exists() && !file.isDirectory) match {
      case Some(archive) => return ZipFileInputStreamFileSystem.fromFile(archive, innerPath)
      case _ =>
    }

    // 路径三：FML 的模组文件。模组尚未加载完成时可能取不到，用 Try 兜住。
    val modFile = Try(FMLLoader.getLoadingModList().getModFileById(domain).getFile.getFilePath.toFile).toOption
    modFile match {
      case Some(file) if !file.isDirectory =>
        ZipFileInputStreamFileSystem.fromFile(file, innerPath)
      case Some(file) =>
        new io.File(file, innerPath) match {
          case fsp if fsp.exists() && fsp.isDirectory => new ReadOnlyFileSystem(fsp)
          case _ =>
            OpenComputers.log.warn(s"Cannot locate file system root '$innerPath' in '${file.getAbsolutePath}'.")
            null
        }
      case _ =>
        OpenComputers.log.warn(s"Cannot locate file system root '$innerPath': mod file for domain '$domain' is unavailable.")
        null
    }
  }

  override def fromSaveDirectory(root: String, capacity: Long, buffered: Boolean): Capacity = {
    val server = ServerLifecycleHooks.getCurrentServer
    if (server == null) {
      // 无服务端上下文时无法定位存档目录（1.7.10 的 `DimensionManager` 此处在无世界时也返回 null）。
      OpenComputers.log.warn(s"Cannot create file system '$root' in the save directory: no server context available.")
      return null
    }
    val path = server.getWorldPath(new LevelResource(Settings.savePath + root)).toFile
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
    Delegator.subItem(fsStack) match {
      case Some(_: FileSystemLike) =>
        val data = li.cil.oc.integration.opencomputers.Item.dataTag(fsStack)
        if (data.contains("node")) {
          val nodeData = data.getCompound("node")
          if (nodeData.contains("address")) {
            nodeData.remove("address")
            return true
          }
        }
      case _ =>
    }
    false
  }

  def fromMemory(capacity: Long): api.fs.FileSystem = new RamFileSystem(capacity)

  def fromComputerCraft(mount: AnyRef): api.fs.FileSystem =
    // 原实现为 `if (Mods.ComputerCraft.isAvailable) DriverComputerCraftMedia.createFileSystem(mount).orNull else null`，
    // 但本项目尚未移植 `li.cil.oc.integration.computercraft` 包，这里等价于「CC 不可用」分支。
    null

  override def asReadOnly(fileSystem: api.fs.FileSystem): api.fs.FileSystem =
    if (fileSystem.isReadOnly) fileSystem
    else {
      new ReadOnlyWrapper(fileSystem)
    }

  def asManagedEnvironment(fileSystem: api.fs.FileSystem, label: Label, host: EnvironmentHost, accessSound: String, speed: Int) =
    Option(fileSystem).flatMap(fs => Some(new component.FileSystem(fs, label, Option(host), Option(accessSound), (speed - 1) max 0 min 5))).orNull

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

  class ReadOnlyLabel(val label: String) extends Label {
    def setLabel(value: String) = throw new IllegalArgumentException("label is read only")

    def getLabel = label

    override def load(nbt: CompoundTag) {}

    override def save(nbt: CompoundTag): Unit = {
      if (label != null) {
        nbt.putString(Settings.namespace + "fs.label", label)
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
