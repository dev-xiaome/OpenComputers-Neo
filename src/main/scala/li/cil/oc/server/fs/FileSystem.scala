package li.cil.oc.server.fs

import java.io
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.UUID

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.server.component
import li.cil.oc.util.ItemNBT
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.util.Try

object FileSystem extends api.detail.FileSystemAPI {

  /**
   * 1.21.1 的存档根目录，等价于 1.7.10 的 `DimensionManager.getCurrentSaveRootDirectory`。
   *
   * NeoForge 1.21.1 已移除 `net.minecraftforge.common.DimensionManager`，改为：
   * `ServerLifecycleHooks.getCurrentServer` 取当前服务端，再用
   * `MinecraftServer#getWorldPath(LevelResource.ROOT)` 取存档根目录（`saves/<世界名>`）。
   *
   * 无服务端上下文（启动早期 / 纯客户端）时返回 `null`，调用方需自行判空。
   */
  def saveRootDirectory: io.File = {
    val server = ServerLifecycleHooks.getCurrentServer
    if (server == null) null
    else server.getWorldPath(LevelResource.ROOT).toFile
  }

  lazy val isCaseInsensitive: Boolean = Settings.get.forceCaseInsensitive || (try {
    val uuid = UUID.randomUUID().toString
    val root = saveRootDirectory
    if (root == null) {
      throw new IllegalStateException("no server context available to detect file system case sensitivity")
    }
    val lowerCase = new io.File(root, uuid + "oc_rox")
    val upperCase = new io.File(root, uuid + "OC_ROX")
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
    val innerPath = ("/assets/" + domain + "/" + (root.trim + "/")).replace("//", "/")

    val codeSource = clazz.getProtectionDomain.getCodeSource.getLocation.getPath
    val (codeUrl, isArchive) =
      if (codeSource.contains(".zip!") || codeSource.contains(".jar!"))
        (codeSource.substring(0, codeSource.lastIndexOf('!')), true)
      else
        (codeSource, false)

    val url = Try {
      new URL(codeUrl)
    }.recoverWith {
      case _: MalformedURLException => Try {
        new URL("file://" + codeUrl)
      }
    }
    val file = url.map(url => new io.File(url.toURI)).recoverWith {
      case _: URISyntaxException => url.map(url => new io.File(url.getPath))
    }.getOrElse(new io.File(codeSource))

    if (isArchive) {
      ZipFileInputStreamFileSystem.fromFile(file, innerPath.substring(1))
    }
    else {
      if (!file.exists || file.isDirectory) return null
      new io.File(new io.File(file.getParent), innerPath) match {
        case fsp if fsp.exists() && fsp.isDirectory =>
          new ReadOnlyFileSystem(fsp)
        case _ =>
          System.getProperty("java.class.path").split(System.getProperty("path.separator")).
            find(cp => {
              val fsp = new io.File(new io.File(cp), innerPath)
              fsp.exists() && fsp.isDirectory
            }) match {
            case None => null
            case Some(dir) => new ReadOnlyFileSystem(new io.File(new io.File(dir), innerPath))
          }
      }
    }
  }

  override def fromSaveDirectory(root: String, capacity: Long, buffered: Boolean): Capacity = {
    val saveRoot = saveRootDirectory
    if (saveRoot == null) {
      // TODO(server): 无服务端上下文时无法定位存档目录（原 1.7.10 在此处会直接 NPE）。
      // 这里显式返回 null，由调用方按「创建文件系统失败」处理。
      OpenComputers.log.warn(s"Cannot create file system '$root' in the save directory: no server context available.")
      return null
    }
    val path = new io.File(saveRoot, Settings.savePath + root)
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
        // TODO(integration): 原实现取 `li.cil.oc.integration.opencomputers.Item.dataTag`，
        // 该包尚未移植，这里改用已移植的 `ItemNBT` 做等价实现（同样返回 `<namespace>data` 子标签）。
        val data = dataTag(fsStack)
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

  /**
   * 等价于原 `li.cil.oc.integration.opencomputers.Item.dataTag`：
   * 确保物品上存在 `oc:data` 复合标签并返回它。
   */
  private def dataTag(stack: ItemStack): CompoundTag = {
    val nbt = ItemNBT.getOrCreate(stack)
    val key = Settings.namespace + "data"
    if (!nbt.contains(key)) {
      nbt.put(key, new CompoundTag())
    }
    nbt.getCompound(key)
  }

  def fromMemory(capacity: Long): api.fs.FileSystem = new RamFileSystem(capacity)

  /**
   * TODO(integration): 原实现依赖 `li.cil.oc.integration.Mods` 与
   * `li.cil.oc.integration.computercraft.DriverComputerCraftMedia`，二者都属于尚未移植的
   * `li.cil.oc.integration` 包。1.21.1 版本暂不提供 ComputerCraft 挂载支持，
   * 因此这里按「CC 不可用」的语义直接返回 `null`。
   */
  def fromComputerCraft(mount: AnyRef): api.fs.FileSystem = null

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
