package li.cil.oc.server.component

import java.io
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import com.google.common.io.Files
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.fs.Label
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.server.fs.FileSystem
// 1.21.1：NeoForge 已移除 `net.minecraftforge.common.DimensionManager`，
// 存档目录改由 `FileSystem.saveRootDirectory`（`ServerLifecycleHooks` + `LevelResource.ROOT`）提供。
import net.minecraft.nbt.CompoundTag
// 显式导入结果包装器，使本文件不依赖 `server/component/package.scala` 也能编译；
// 与包对象里的隐式转换 `result` 语义完全一致（同一实现）。
import li.cil.oc.util.ResultWrapper.result

import scala.jdk.CollectionConverters._

class Drive(val capacity: Int, val platterCount: Int, val label: Label, host: Option[EnvironmentHost], val sound: Option[String], val speed: Int, val isLocked: Boolean) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("drive", Visibility.Neighbors).
    withConnector().
    create()

  /**
   * 驱动器数据文件的落盘路径。
   *
   * 1.21.1：无服务端上下文时 `FileSystem.saveRootDirectory` 返回 `null`，
   * 此时同样返回 `null`，由 `load` / `save` 跳过磁盘读写（原 1.7.10 在此处会 NPE）。
   */
  private def savePath: io.File = {
    val root = FileSystem.saveRootDirectory
    if (root == null) null
    else new io.File(root, Settings.savePath + node.address + ".bin")
  }

  private final val sectorSize = 512

  private val data = new Array[Byte](capacity)

  private val sectorCount = capacity / sectorSize

  private val sectorsPerPlatter = sectorCount / platterCount

  private var headPos = 0

  final val readSectorCosts = Array(1.0 / 10, 1.0 / 20, 1.0 / 30, 1.0 / 40, 1.0 / 50, 1.0 / 60)
  final val writeSectorCosts = Array(1.0 / 5, 1.0 / 10, 1.0 / 15, 1.0 / 20, 1.0 / 25, 1.0 / 30)
  final val readByteCosts = Array(1.0 / 48, 1.0 / 64, 1.0 / 80, 1.0 / 96, 1.0 / 112, 1.0 / 128)
  final val writeByteCosts = Array(1.0 / 24, 1.0 / 32, 1.0 / 40, 1.0 / 48, 1.0 / 56, 1.0 / 64)

  // ----------------------------------------------------------------------- //

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Disk,
    DeviceAttribute.Description -> "Hard disk drive",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> ("MPD" + (capacity / 1024).toString + "L" + platterCount.toString),
    DeviceAttribute.Capacity -> (capacity * 1.024).toInt.toString,
    DeviceAttribute.Size -> capacity.toString,
    DeviceAttribute.Clock -> (((2000 / readSectorCosts(speed)).toInt / 100).toString + "/" + ((2000 / writeSectorCosts(speed)).toInt / 100).toString + "/" + ((2000 / readByteCosts(speed)).toInt / 100).toString + "/" + ((2000 / writeByteCosts(speed)).toInt / 100).toString)
  )

  // 1.21.1：原来靠 `scala.collection.convert.WrapAsJava._` 提供的隐式转换已随 Scala 2.13 移除，
  // 这里显式用 `CollectionConverters` 的 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():string -- Get the current label of the drive.""")
  def getLabel(context: Context, args: Arguments): Array[AnyRef] = this.synchronized {
    if (label != null) result(label.getLabel) else null
  }

  @Callback(doc = """function(value:string):string -- Sets the label of the drive. Returns the new value, which may be truncated.""")
  def setLabel(context: Context, args: Arguments): Array[AnyRef] = this.synchronized {
    if (isLocked) throw new Exception("drive is read only")
    if (label == null) throw new Exception("drive does not support labeling")
    if (args.checkAny(0) == null) label.setLabel(null)
    else label.setLabel(args.checkString(0))
    result(label.getLabel)
  }

  @Callback(direct = true, doc = """function():number -- Returns the total capacity of the drive, in bytes.""")
  def getCapacity(context: Context, args: Arguments): Array[AnyRef] = result(capacity)

  @Callback(direct = true, doc = """function():number -- Returns the size of a single sector on the drive, in bytes.""")
  def getSectorSize(context: Context, args: Arguments): Array[AnyRef] = result(sectorSize)

  @Callback(direct = true, doc = """function():number -- Returns the number of platters in the drive.""")
  def getPlatterCount(context: Context, args: Arguments): Array[AnyRef] = result(platterCount)

  @Callback(direct = true, doc = """function(sector:number):string -- Read the current contents of the specified sector.""")
  def readSector(context: Context, args: Arguments): Array[AnyRef] = this.synchronized {
    context.consumeCallBudget(readSectorCosts(speed))
    val sector = moveToSector(context, checkSector(args, 0))
    diskActivity()
    val sectorData = new Array[Byte](sectorSize)
    Array.copy(data, sectorOffset(sector), sectorData, 0, sectorSize)
    result(sectorData)
  }

  @Callback(direct = true, doc = """function(sector:number, value:string) -- Write the specified contents to the specified sector.""")
  def writeSector(context: Context, args: Arguments): Array[AnyRef] = this.synchronized {
    if (isLocked) throw new Exception("drive is read only")
    context.consumeCallBudget(writeSectorCosts(speed))
    val sectorData = args.checkByteArray(1)
    val sector = moveToSector(context, checkSector(args, 0))
    diskActivity()
    Array.copy(sectorData, 0, data, sectorOffset(sector), math.min(sectorSize, sectorData.length))
    null
  }

  @Callback(direct = true, doc = """function(offset:number):number -- Read a single byte at the specified offset.""")
  def readByte(context: Context, args: Arguments): Array[AnyRef] = this.synchronized {
    context.consumeCallBudget(readByteCosts(speed))
    val offset = args.checkInteger(0) - 1
    moveToSector(context, checkSector(offset))
    diskActivity()
    result(data(offset))
  }

  @Callback(direct = true, doc = """function(offset:number, value:number) -- Write a single byte to the specified offset.""")
  def writeByte(context: Context, args: Arguments): Array[AnyRef] = this.synchronized {
    if (isLocked) throw new Exception("drive is read only")
    context.consumeCallBudget(writeByteCosts(speed))
    val offset = args.checkInteger(0) - 1
    val value = args.checkInteger(1).toByte
    moveToSector(context, checkSector(offset))
    diskActivity()
    data(offset) = value
    null
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag) = this.synchronized {
    super.load(nbt)

    if (node.address != null) try {
      val path = savePath
      if (path != null && path.exists()) {
        val bin = new ByteArrayInputStream(Files.toByteArray(path))
        val zin = new GZIPInputStream(bin)
        var offset = 0
        var read = 0
        while (read >= 0 && offset < data.length) {
          read = zin.read(data, offset, data.length - offset)
          offset += read
        }
      }
    }
    catch {
      case t: Throwable => OpenComputers.log.warn(s"Failed loading drive contents for '${node.address}'.", t)
    }

    // 1.21.1：`getInteger` → `getInt`。
    headPos = nbt.getInt("headPos") max 0 min sectorToHeadPos(sectorCount)

    if (label != null) {
      label.load(nbt)
    }
  }

  override def save(nbt: CompoundTag) = this.synchronized {
    super.save(nbt)

    if (node.address != null) try {
      val path = savePath
      if (path != null) {
        path.getParentFile.mkdirs()
        val bos = new ByteArrayOutputStream()
        val zos = new GZIPOutputStream(bos)
        zos.write(data)
        zos.close()
        Files.write(bos.toByteArray, path)
      }
    }
    catch {
      case t: Throwable => OpenComputers.log.warn(s"Failed saving drive contents for '${node.address}'.", t)
    }

    nbt.putInt("headPos", headPos)

    if (label != null) {
      label.save(nbt)
    }
  }

  // ----------------------------------------------------------------------- //

  private def validateSector(sector: Int) = {
    if (sector < 0 || sector >= sectorCount)
      throw new IllegalArgumentException("invalid offset, not in a usable sector")
    sector
  }

  private def checkSector(offset: Int) = validateSector(offsetSector(offset))

  private def checkSector(args: Arguments, n: Int) = validateSector(args.checkInteger(n) - 1)

  private def moveToSector(context: Context, sector: Int) = {
    val newHeadPos = sectorToHeadPos(sector)
    if (headPos != newHeadPos) {
      val delta = math.abs(headPos - newHeadPos)
      if (delta > Settings.get.sectorSeekThreshold) context.pause(Settings.get.sectorSeekTime)
      headPos = newHeadPos
    }
    sector
  }

  private def sectorToHeadPos(sector: Int) = sector % sectorsPerPlatter

  private def sectorOffset(sector: Int) = sector * sectorSize

  private def offsetSector(offset: Int) = offset / sectorSize

  /**
   * TODO(server): 磁盘访问音效通告。
   *
   * 原实现调用 `li.cil.oc.server.PacketSender.sendFileSystemActivity(...)`（投递
   * `FileSystemAccessEvent.Server` 事件 + 发送 `PacketType.FileSystemActivity` 包）。
   * `server/PacketSender` 与 `common/network/message` 尚未移植，这里降级为空操作：
   * 只影响客户端播放磁盘访问音效，不影响驱动器读写功能。
   */
  private def diskActivity(): Unit = ()
}
