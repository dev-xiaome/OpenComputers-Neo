package li.cil.oc.server.component

import java.io.FileNotFoundException
import java.io.IOException
import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.fs.Label
import li.cil.oc.api.fs.Mode
import li.cil.oc.api.fs.{FileSystem => IFileSystem}
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.api.prefab.AbstractValue
// 1.21.1：原 `net.minecraftforge.common.util.Constants.NBT` 已移除，改用 `Tag.TAG_*` 常量。
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
// 显式导入结果包装器，使本文件不依赖 `server/component/package.scala` 也能编译；
// 与包对象里的隐式转换 `result` 语义完全一致（同一实现）。
import li.cil.oc.util.ResultWrapper.result
// `setNewCompoundTag` 等 NBT 扩展（1.21.1 API 适配层）。
import li.cil.oc.util.ExtendedNBT._

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class FileSystem(val fileSystem: IFileSystem, var label: Label, val host: Option[EnvironmentHost], val sound: Option[String], val speed: Int) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("filesystem", Visibility.Neighbors).
    withConnector().
    create()

  private val owners = mutable.Map.empty[String, mutable.Set[Int]]

  final val readCosts = Array(1.0 / 1, 1.0 / 4, 1.0 / 7, 1.0 / 10, 1.0 / 13, 1.0 / 15)
  final val seekCosts = Array(1.0 / 1, 1.0 / 4, 1.0 / 7, 1.0 / 10, 1.0 / 13, 1.0 / 15)
  final val writeCosts = Array(1.0 / 1, 1.0 / 2, 1.0 / 3, 1.0 / 4, 1.0 / 5, 1.0 / 6)

  // ----------------------------------------------------------------------- //

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Volume,
    DeviceAttribute.Description -> "Filesystem",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "MPFS.21.6",
    DeviceAttribute.Capacity -> (fileSystem.spaceTotal * 1.024).toInt.toString,
    DeviceAttribute.Size -> fileSystem.spaceTotal.toString,
    DeviceAttribute.Clock -> (((2000 / readCosts(speed)).toInt / 100).toString + "/" + ((2000 / seekCosts(speed)).toInt / 100).toString + "/" + ((2000 / writeCosts(speed)).toInt / 100).toString)
  )

  // 1.21.1：原来靠 `scala.collection.convert.WrapAsJava._` 提供的隐式转换已随 Scala 2.13 移除，
  // 这里显式用 `CollectionConverters` 的 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():string -- Get the current label of the drive.""")
  def getLabel(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    if (label != null) result(label.getLabel) else null
  }

  @Callback(doc = """function(value:string):string -- Sets the label of the drive. Returns the new value, which may be truncated.""")
  def setLabel(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    if (label == null) throw new Exception("drive does not support labeling")
    if (args.checkAny(0) == null) label.setLabel(null)
    else label.setLabel(args.checkString(0))
    result(label.getLabel)
  }

  @Callback(direct = true, doc = """function():boolean -- Returns whether the file system is read-only.""")
  def isReadOnly(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    result(fileSystem.isReadOnly)
  }

  @Callback(direct = true, doc = """function():number -- The overall capacity of the file system, in bytes.""")
  def spaceTotal(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    val space = fileSystem.spaceTotal
    if (space < 0) result(Double.PositiveInfinity)
    else result(space)
  }

  @Callback(direct = true, doc = """function():number -- The currently used capacity of the file system, in bytes.""")
  def spaceUsed(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    result(fileSystem.spaceUsed)
  }

  @Callback(direct = true, doc = """function(path:string):boolean -- Returns whether an object exists at the specified absolute path in the file system.""")
  def exists(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    diskActivity()
    result(fileSystem.exists(clean(args.checkString(0))))
  }

  @Callback(direct = true, doc = """function(path:string):number -- Returns the size of the object at the specified absolute path in the file system.""")
  def size(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    diskActivity()
    result(fileSystem.size(clean(args.checkString(0))))
  }

  @Callback(direct = true, doc = """function(path:string):boolean -- Returns whether the object at the specified absolute path in the file system is a directory.""")
  def isDirectory(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    diskActivity()
    result(fileSystem.isDirectory(clean(args.checkString(0))))
  }

  @Callback(direct = true, doc = """function(path:string):number -- Returns the (real world) timestamp of when the object at the specified absolute path in the file system was modified.""")
  def lastModified(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    diskActivity()
    result(fileSystem.lastModified(clean(args.checkString(0))))
  }

  @Callback(doc = """function(path:string):table -- Returns a list of names of objects in the directory at the specified absolute path in the file system.""")
  def list(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    Option(fileSystem.list(clean(args.checkString(0)))) match {
      case Some(list) =>
        diskActivity()
        Array(list)
      case _ => null
    }
  }

  @Callback(doc = """function(path:string):boolean -- Creates a directory at the specified absolute path in the file system. Creates parent directories, if necessary.""")
  def makeDirectory(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    def recurse(path: String): Boolean = !fileSystem.exists(path) && (fileSystem.makeDirectory(path) ||
      (recurse(path.split("/").dropRight(1).mkString("/")) && fileSystem.makeDirectory(path)))
    val success = recurse(clean(args.checkString(0)))
    diskActivity()
    result(success)
  }

  @Callback(doc = """function(path:string):boolean -- Removes the object at the specified absolute path in the file system.""")
  def remove(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    def recurse(parent: String): Boolean = (!fileSystem.isDirectory(parent) ||
      fileSystem.list(parent).forall(child => recurse(parent + "/" + child))) && fileSystem.delete(parent)
    val success = recurse(clean(args.checkString(0)))
    diskActivity()
    result(success)
  }

  @Callback(doc = """function(from:string, to:string):boolean -- Renames/moves an object from the first specified absolute path in the file system to the second.""")
  def rename(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    val success = fileSystem.rename(clean(args.checkString(0)), clean(args.checkString(1)))
    diskActivity()
    result(success)
  }

  @Callback(direct = true, doc = """function(handle:userdata) -- Closes an open file descriptor with the specified handle.""")
  def close(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    close(context, checkHandle(args, 0))
    null
  }

  @Callback(direct = true, limit = 4, doc = """function(path:string[, mode:string='r']):userdata -- Opens a new file descriptor and returns its handle.""")
  def open(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    if (owners.get(context.node.address).fold(false)(_.size >= Settings.get.maxHandles)) {
      throw new IOException("too many open handles")
    }
    val path = args.checkString(0)
    val mode = args.optString(1, "r")
    val handle = fileSystem.open(clean(path), parseMode(mode))
    if (handle > 0) {
      owners.getOrElseUpdate(context.node.address, mutable.Set.empty[Int]) += handle
    }
    diskActivity()
    result(new HandleValue(node.address, handle))
  }

  @Callback(direct = true, limit = 15, doc = """function(handle:userdata, count:number):string or nil -- Reads up to the specified amount of data from an open file descriptor with the specified handle. Returns nil when EOF is reached.""")
  def read(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    context.consumeCallBudget(readCosts(speed))
    val handle = checkHandle(args, 0)
    val n = math.min(Settings.get.maxReadBuffer, math.max(0, args.checkInteger(1)))
    checkOwner(context.node.address, handle)
    Option(fileSystem.getHandle(handle)) match {
      case Some(file) =>
        // Limit size of read buffer to avoid crazy allocations.
        val buffer = new Array[Byte](n)
        val read = file.read(buffer)
        if (read >= 0) {
          val bytes =
            if (read == buffer.length)
              buffer
            else {
              val bytes = new Array[Byte](read)
              Array.copy(buffer, 0, bytes, 0, read)
              bytes
            }
          if (!node.tryChangeBuffer(-Settings.get.hddReadCost * bytes.length)) {
            throw new IOException("not enough energy")
          }
          diskActivity()
          result(bytes)
        }
        else {
          result(())
        }
      case _ => throw new IOException("bad file descriptor")
    }
  }

  @Callback(direct = true, doc = """function(handle:userdata, whence:string, offset:number):number -- Seeks in an open file descriptor with the specified handle. Returns the new pointer position.""")
  def seek(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    context.consumeCallBudget(seekCosts(speed))
    val handle = checkHandle(args, 0)
    val whence = args.checkString(1)
    val offset = args.checkInteger(2)
    checkOwner(context.node.address, handle)
    Option(fileSystem.getHandle(handle)) match {
      case Some(file) =>
        whence match {
          case "cur" => file.seek(file.position + offset)
          case "set" => file.seek(offset)
          case "end" => file.seek(file.length + offset)
          case _ => throw new IllegalArgumentException("invalid mode")
        }
        result(file.position)
      case _ => throw new IOException("bad file descriptor")
    }
  }

  @Callback(direct = true, doc = """function(handle:userdata, value:string):boolean -- Writes the specified data to an open file descriptor with the specified handle.""")
  def write(context: Context, args: Arguments): Array[AnyRef] = fileSystem.synchronized {
    context.consumeCallBudget(writeCosts(speed))
    val handle = checkHandle(args, 0)
    val value = args.checkByteArray(1)
    if (!node.tryChangeBuffer(-Settings.get.hddWriteCost * value.length)) {
      throw new IOException("not enough energy")
    }
    checkOwner(context.node.address, handle)
    Option(fileSystem.getHandle(handle)) match {
      case Some(file) =>
        file.write(value)
        diskActivity()
        result(true)
      case _ => throw new IOException("bad file descriptor")
    }
  }

  // ----------------------------------------------------------------------- //

  def checkHandle(args: Arguments, index: Int) = {
    if (args.isInteger(index)) {
      args.checkInteger(index)
    } else if (args.isTable(index)) {
      args.checkTable(index).get("handle") match {
        case handle: Number => handle.intValue()
        case _ => throw new IOException("bad file descriptor")
      }
    } else args.checkAny(index) match {
      case handle: HandleValue => handle.handle
      case _ => throw new IOException("bad file descriptor")
    }
  }

  def close(context: Context, handle: Int): Unit = {
    Option(fileSystem.getHandle(handle)) match {
      case Some(file) =>
        owners.get(context.node.address) match {
          case Some(set) if set.remove(handle) => file.close()
          case _ => throw new IOException("bad file descriptor")
        }
      case _ => throw new IOException("bad file descriptor")
    }
  }

  // ----------------------------------------------------------------------- //

  override def onMessage(message: Message) = fileSystem.synchronized {
    super.onMessage(message)
    if (message.name == "computer.stopped" || message.name == "computer.started") {
      owners.get(message.source.address) match {
        case Some(set) =>
          set.foreach(handle => Option(fileSystem.getHandle(handle)) match {
            case Some(file) => file.close()
            case _ => // Invalid handle... huh.
          })
          set.clear()
        case _ => // Computer had no open files.
      }
    }
  }

  override def onDisconnect(node: Node) = fileSystem.synchronized {
    super.onDisconnect(node)
    if (node == this.node) {
      fileSystem.close()
    }
    else if (owners.contains(node.address)) {
      for (handle <- owners(node.address)) {
        Option(fileSystem.getHandle(handle)) match {
          case Some(file) => file.close()
          case _ =>
        }
      }
      owners.remove(node.address)
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)

    // 1.21.1：`getList(k, type)` 的类型参数改为 `Tag.TAG_COMPOUND`；
    // `tagCount` / `getCompoundTagAt` → `size()` / `getCompound(i)`。
    val ownersNbt = nbt.getList("owners", Tag.TAG_COMPOUND)
    for (i <- 0 until ownersNbt.size()) {
      val ownerNbt = ownersNbt.getCompound(i)
      val address = ownerNbt.getString("address")
      if (address != "") {
        // Scala 2.13：`Array#to[mutable.Set]` 不再接受裸类型构造器，改为显式构造可变集合。
        val handles = ownerNbt.getIntArray("handles")
        owners += address -> mutable.Set(handles.toSeq: _*)
      }
    }

    if (label != null) {
      label.load(nbt)
    }
    fileSystem.load(nbt.getCompound("fs"))
  }

  override def save(nbt: CompoundTag) = fileSystem.synchronized {
    super.save(nbt)

    if (label != null) {
      label.save(nbt)
    }

    if (!savingForClients) {
      val ownersNbt = new ListTag()
      for ((address, handles) <- owners) {
        val ownerNbt = new CompoundTag()
        ownerNbt.putString("address", address)
        ownerNbt.put("handles", new IntArrayTag(handles.toArray))
        ownersNbt.add(ownerNbt)
      }
      nbt.put("owners", ownersNbt)

      nbt.setNewCompoundTag("fs", fileSystem.save)
    }
  }

  // ----------------------------------------------------------------------- //

  private def clean(path: String) = {
    val result = com.google.common.io.Files.simplifyPath(path)
    if (result.startsWith("../") || result == "..") throw new FileNotFoundException(path)
    if (result == "/" || result == ".") ""
    else result
  }

  private def parseMode(value: String): Mode = {
    if (("r" == value) || ("rb" == value)) return Mode.Read
    if (("w" == value) || ("wb" == value)) return Mode.Write
    if (("a" == value) || ("ab" == value)) return Mode.Append
    throw new IllegalArgumentException("unsupported mode")
  }

  private def checkOwner(owner: String, handle: Int) =
    if (!owners.contains(owner) || !owners(owner).contains(handle))
      throw new IOException("bad file descriptor")

  /**
   * 与 CE-1.20 一致：向客户端序列化（发描述包）时跳过 `owners` 数据，只有真正写盘
   * （服务端存档）时才写入。`common/SaveHandler` 已在编译集内，直接用它的开关。
   */
  private def savingForClients: Boolean = li.cil.oc.common.SaveHandler.savingForClients

  /**
   * 磁盘访问音效通告。
   *
   * 与 CE-1.20 一致：[[li.cil.oc.server.PacketSender.sendFileSystemActivity]] 会先向事件总线投递
   * `FileSystemAccessEvent.Server`（允许其它模组改写 / 取消音效），再发送
   * `PacketType.FileSystemActivity` 包给附近玩家；限流由 PacketSender 内部按 host 缓存。
   * （此前这里被降级成空操作，理由是 `server/PacketSender` 未移植 —— 该理由已不成立。）
   */
  private def diskActivity(): Unit = {
    (sound, host) match {
      case (Some(s), Some(h)) => li.cil.oc.server.PacketSender.sendFileSystemActivity(node, h, s)
      case _ =>
    }
  }
}

final class HandleValue extends AbstractValue {
  def this(owner: String, handle: Int) = {
    this()
    this.owner = owner
    this.handle = handle
  }

  var owner = ""
  var handle = 0

  override def dispose(context: Context): Unit = {
    super.dispose(context)
    if (context.node() != null && context.node().network() != null) {
      val node = context.node().network().node(owner)
      if (node != null) {
        node.host() match {
          case fs: FileSystem => try fs.close(context, handle) catch {
            case _: Throwable => // Ignore, already closed.
          }
        }
      }
    }
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    owner = nbt.getString("owner")
    // 1.21.1：`getInteger` → `getInt`。
    handle = nbt.getInt("handle")
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.putInt("handle", handle)
    nbt.putString("owner", owner)
  }

  override def toString: String = handle.toString
}
