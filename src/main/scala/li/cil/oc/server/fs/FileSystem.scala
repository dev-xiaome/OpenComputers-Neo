package li.cil.oc.server.fs

import java.io
import java.net.JarURLConnection
import java.net.URL
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
import li.cil.oc.util.ItemNBT
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLLoader
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
    // 与 1.20 CE / 1.21.1 上游的对照说明：
    //  * 1.20 CE 把这条 API 换成了 `fromResource(ResourceLocation)`，内部用
    //    `FMLLoader.getLoadingModList().getModFileById(命名空间).getFile().getFilePath()`
    //    取模组文件（打包后是 jar 文件，开发环境是目录），再拼 `assets/<命名空间>/<子路径>/`，
    //    目录就包成只读文件系统，jar 就走 `ZipFileInputStreamFileSystem.fromFile`。
    //  * 1.21.1 上游沿用了这个做法，并在它前面补了两条**开发环境**专用路径：
    //    ModDevGradle 的 `fml.modFolders` 系统属性，以及类加载器 `getResource`。
    //  * 本项目的 `api.detail.FileSystemAPI.fromClass(clazz, domain, root)` 签名还被
    //    `common/Loot.scala`、`integration/opencomputers/DriverLootDisk.scala` 与
    //    `server/component/Robot.scala` 使用，改签名会波及禁止改动的 `server/component`，
    //    因此这里**保留签名**，只把内部「怎么定位 classpath 上的资源根」的逻辑替换掉。
    //
    // 旧实现为什么坏：它用 `clazz.getProtectionDomain.getCodeSource.getLocation.getPath`
    // 定位资源根目录。1.21.1 的 ModLauncher / TransformingClassLoader 把模组类装在
    // union / secure-jar 形式的伪路径下（形如 `.../build/classes/scala/main%23199!/`），
    // 该路径既 `exists()` 为 false、也无法当成归档打开；即便侥幸拿到 `build/classes/...`
    // 这样的真实目录，下面那句 `if (!file.exists || file.isDirectory) return null` 也会
    // 直接返回 null（因为 ModDevGradle 把 classes 与 resources 输出到了两棵不同的树）。
    // 于是战利品软盘的工厂回调返回 null、`asManagedEnvironment` 返回 null，软盘压根没有
    // 挂上文件系统，Lua BIOS 自然读不到 `/init.lua`。
    val innerPath = "assets/" + domain + "/" + (root.trim.stripPrefix("/").stripSuffix("/") + "/")

    // ------------------------------------------------------------------ //
    // 路径一：ModDevGradle 开发环境（1.20 CE 没有这条，它假设模组文件路径就是资源根）。
    // ModDevGradle 用系统属性 `fml.modFolders` 把每个 source set 的输出根以
    // `<模组id>%%<绝对路径>` 的形式暴露出来，多个条目用平台路径分隔符连接。
    // 本项目 `build/classes/java/main`、`build/classes/scala/main`、`build/resources/main`
    // 是三个独立的根，只有 `build/resources/main` 底下才有 `assets` 目录，所以必须逐个试。
    // ------------------------------------------------------------------ //
    val modFolderRoots = Option(System.getProperty("fml.modFolders")).toSeq
      .flatMap(_.split(java.util.regex.Pattern.quote(io.File.pathSeparator)))
      .flatMap(entry => entry.split("%%", 2) match {
        case Array(namespace, folderRoot) if namespace.split(",").contains(domain) =>
          Some(new io.File(folderRoot, innerPath))
        case _ => None
      })

    // ------------------------------------------------------------------ //
    // 路径二：类加载器资源查找。与 `getClass.getResourceAsStream("/assets/...")` 同源，
    // 开发环境与「jar 直接在普通类路径上」的两种情形都适用；
    // 资源在归档里时 URL 协议是 `jar`，此时用 `JarURLConnection` 反查归档文件。
    // ------------------------------------------------------------------ //
    val loaders = Seq(Option(clazz.getClassLoader), Option(Thread.currentThread.getContextClassLoader)).flatten.distinct
    val resourceUrls = loaders.flatMap(loader => Option(loader.getResource(innerPath)))

    // ------------------------------------------------------------------ //
    // 路径三：FML 模组文件（1.20 CE 与 1.21.1 上游的主路径）。
    // 打包后 `getFilePath` 指向模组 jar，开发环境指向某个输出目录。
    // 用 `Try` 包住：模组尚未加载完成时 `getLoadingModList` / `getModFileById` 可能返回 null。
    // ------------------------------------------------------------------ //
    val modFile = Try(FMLLoader.getLoadingModList().getModFileById(domain).getFile.getFilePath.toFile).toOption

    def asDirectory(file: io.File): Option[api.fs.FileSystem] =
      if (file.exists() && file.isDirectory) Some(new ReadOnlyFileSystem(file)) else None

    def asArchive(file: io.File): Option[api.fs.FileSystem] =
      if (file.exists() && !file.isDirectory) Option(ZipFileInputStreamFileSystem.fromFile(file, innerPath)) else None

    def asFileUrl(url: URL): Option[api.fs.FileSystem] =
      if (url.getProtocol != "file") None
      else asDirectory(Try(Paths.get(url.toURI).toFile).getOrElse(new io.File(url.getPath)))

    def asJarUrl(url: URL): Option[api.fs.FileSystem] =
      if (url.getProtocol != "jar") None
      else Try(url.openConnection().asInstanceOf[JarURLConnection].getJarFileURL.toURI).toOption
        .map(uri => new io.File(uri)).flatMap(asArchive)

    // 按优先级依次尝试，第一个成功的即结果。
    val attempts: Seq[(String, () => Option[api.fs.FileSystem])] = Seq(
      "fml.modFolders" -> (() => modFolderRoots.iterator.flatMap(asDirectory).nextOption()),
      "classLoader:file" -> (() => resourceUrls.iterator.flatMap(asFileUrl).nextOption()),
      "classLoader:jar" -> (() => resourceUrls.iterator.flatMap(asJarUrl).nextOption()),
      "modFile:dir" -> (() => modFile.iterator.flatMap(asDirectory).nextOption()),
      "modFile:jar" -> (() => modFile.iterator.flatMap(asArchive).nextOption()))

    val hit = attempts.iterator.map { case (name, attempt) => (name, attempt()) }.find(_._2.isDefined)

    // TODO(diag): 临时诊断日志，用于确认「软盘 / 机器人 Lua 资源能否定位到」，
    // 实机验证通过后删除。`entryCount` 若为 -1 表示目录不可列举。
    OpenComputers.log.info(
      s"[OC-DIAG] fromClass(domain=$domain, root=$root): innerPath=$innerPath " +
        s"modFolderRoots=[${modFolderRoots.map(f => f.getAbsolutePath + "(" + entryCount(f) + " 项)").mkString(", ")}] " +
        s"resourceUrls=[${resourceUrls.map(u => u.getProtocol + ":" + u).mkString(", ")}] " +
        s"modFile=${modFile.map(_.getAbsolutePath).getOrElse("null")} " +
        s"命中=${hit.map(_._1).getOrElse("未命中 -> 返回 null")}")

    hit.flatMap(_._2).orNull
  }

  /** 目录下的一级条目数，仅用于诊断日志。 */
  private def entryCount(directory: io.File): Int =
    Option(directory.list()).map(_.length).getOrElse(-1)

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
