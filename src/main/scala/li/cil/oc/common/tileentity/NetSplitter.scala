package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.{SoundEvents, SoundSource}
import net.minecraft.world.level.block.state.BlockState

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 网络分线器（原 1.7.10 `common.tileentity.NetSplitter`）：按面开启 / 关闭网络连接，
 * 并可由红石信号整体反转各面的开关状态。
 *
 * 纹理：下/上 = NetSplitterTop，北 = NetSplitterFront，其它 = NetSplitterSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反推（见 [[BlockEntityBase.typeOf]]）。
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`；
 *    `ForgeDirection.getOrientation(i)` → `Direction.from3DDataValue(i)`
 *    （1.21.1 的实现对越界值做取模，不会抛异常）。
 *  - `world.playSoundEffect` → `Level#playSound`（音效名 → 注册过的
 *    [[net.minecraft.sounds.SoundEvents]]）；`world.rand` → `world.getRandom`。
 *  - `world.notifyBlocksOfNeighborChange(x, y, z, block)` → [[li.cil.oc.common.tileentity.traits.TileEntity#notifyNeighbors]]；
 *    `world.markBlockForUpdate(x, y, z)` → [[li.cil.oc.common.tileentity.traits.TileEntity#markBlockForUpdate]]。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）；`canConnect` 本来就只在客户端调用。
 *
 * 降级：
 *  - `common.EventHandler.scheduleServer(this)` 未移植，见 [[initialize]] 的 TODO；
 *    [[li.cil.oc.common.tileentity.traits.Environment]] 已在 `initialize()` 里直接
 *    `api.Network.joinOrCreateNetwork(this)`，语义等价。
 *  - `ServerPacketSender.sendNetSplitterState(this)` → [[markBlockForUpdate]] + TODO。
 */
class NetSplitter(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.OpenSides with traits.RedstoneAware with api.network.SidedEnvironment with DeviceInfo {

  private lazy val deviceInfo: util.Map[String, String] = Map(
    DeviceAttribute.Class -> DeviceClass.Network,
    DeviceAttribute.Description -> "Ethernet controller",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "NetSplits",
    DeviceAttribute.Version -> "1.0",
    DeviceAttribute.Width -> "6"
  ).asJava

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  _isOutputEnabled = true

  val node: Node = api.Network.newNode(this, Visibility.Network).
    withComponent("net_splitter", Visibility.Network).
    create()

  var isInverted = false

  override def isSideOpen(side: Direction): Boolean = if (isInverted) !super.isSideOpen(side) else super.isSideOpen(side)

  override def setSideOpen(side: Direction, value: Boolean): Unit = {
    val previous = isSideOpen(side)
    super.setSideOpen(side, value)
    if (previous != isSideOpen(side)) {
      if (isServer) {
        node.remove()
        api.Network.joinOrCreateNetwork(this)
        // TODO(server.PacketSender): 原为 ServerPacketSender.sendNetSplitterState(this)。
        markBlockForUpdate()
        world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS,
          0.5f, world.getRandom.nextFloat() * 0.25f + 0.7f)
        notifyNeighbors()
      }
      else {
        markBlockForUpdate()
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def sidedNode(side: Direction): Node = if (isSideOpen(side)) node else null

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（该方法只应由客户端渲染调用）。
  override def canConnect(side: Direction): Boolean = isSideOpen(side)

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = false

  override protected def initialize(): Unit = {
    super.initialize()
    // 原实现：EventHandler.scheduleServer(this)（把「加入网络」推迟到下一个服务端 tick）。
    // TODO(common.EventHandler): `common.EventHandler` 未纳入编译范围；1.21.1 的
    // `BlockEntity#onLoad()` 已经是「方块实体完整加入世界之后」的时机，
    // 且 traits.Environment 已在这里调用 api.Network.joinOrCreateNetwork(this)，无需再调度。
  }

  // ----------------------------------------------------------------------- //

  override protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    val oldIsInverted = isInverted
    isInverted = args.newValue > 0
    if (isInverted != oldIsInverted) {
      if (isServer) {
        node.remove()
        api.Network.joinOrCreateNetwork(this)
        // TODO(server.PacketSender): 原为 ServerPacketSender.sendNetSplitterState(this)。
        markBlockForUpdate()
        world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS,
          0.5f, world.getRandom.nextFloat() * 0.25f + 0.7f)
      }
      else {
        markBlockForUpdate()
      }
    }
  }

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    isInverted = nbt.getBoolean(Settings.namespace + "isInverted")
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putBoolean(Settings.namespace + "isInverted", isInverted)
  }

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解。
  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    isInverted = nbt.getBoolean(Settings.namespace + "isInverted")
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putBoolean(Settings.namespace + "isInverted", isInverted)
  }

  // component api
  def currentStatus(): mutable.Map[Int, Boolean] = {
    val openSides = mutable.Map[Int, Boolean]()
    for (side <- Direction.values()) {
      openSides += side.ordinal() -> isSideOpen(side)
    }
    openSides
  }

  def setSide(side: Direction, state: Boolean): Boolean = {
    val previous = isSideOpen(side) // isSideOpen uses inverter
    setSideOpen(side, if (isInverted) !state else state) // but setSideOpen does not
    previous != state
  }

  @Callback(doc = "function(settings:table):table -- set open state (true/false) of all sides in an array; index by direction. Returns previous states")
  def setSides(context: Context, args: Arguments): Array[AnyRef] = {
    val settings = args.checkTable(0)
    val previous = currentStatus()
    for (side <- Direction.values()) {
      val ordinal = side.ordinal()
      val value = if (settings.containsKey(ordinal)) {
        settings.get(ordinal) match {
          case v: Boolean => v
          case _ => false
        }
      } else false
      setSide(side, value)
    }
    result(previous)
  }

  @Callback(direct = true, doc = "function():table -- Returns current open/close state of all sides in an array, indexed by direction.")
  def getSides(context: Context, args: Arguments): Array[AnyRef] = result(currentStatus())

  def setSideHelper(args: Arguments, value: Boolean): Array[AnyRef] = {
    val side = Direction.from3DDataValue(args.checkInteger(0))
    if (!Direction.values().contains(side))
      return result(Unit, "invalid direction")
    result(setSide(side, value))
  }

  @Callback(doc = "function(side: number):boolean -- Open the side, returns true if it changed to open.")
  def open(context: Context, args: Arguments): Array[AnyRef] = setSideHelper(args, value = true)

  @Callback(doc = "function(side: number):boolean -- Close the side, returns true if it changed to close.")
  def close(context: Context, args: Arguments): Array[AnyRef] = setSideHelper(args, value = false)
}
