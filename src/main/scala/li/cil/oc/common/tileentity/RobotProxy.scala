package li.cil.oc.common.tileentity

import java.util.UUID

import li.cil.oc.api
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.minecraft.server.level.ServerLevel

/**
 * 机器人代理方块实体（对应 1.7.10 的 `tileentity.RobotProxy`）。
 *
 * 1.7.10 里 `RobotProxy` 是持有 `val robot: Robot` 的方块实体；1.21.1 的方块实体构造函数只有
 * `(pos, state)`，因此机器人实例由代理**自己创建**（见 [[robot]]）。
 *
 * ==降级清单（详见各处的 TODO）==
 *  - `server.PacketSender`（状态同步包）、`server.agent.*`（机器人假玩家 / 动作执行）、
 *    `li.cil.oc.client.gui`（GUI 关闭逻辑）、`integration.*`（红石卡 / 扳手 / RedLogic）
 *    均未移植，逐处给出最小可用实现。
 *  - 原 1.7.10 的 `IInventory` / `ISidedInventory` / `IFluidHandler` 接口在 1.21.1 由
 *    `IItemHandler` / NeoForge `IFluidHandler` 取代，对外通过
 *    [[li.cil.oc.common.tileentity.BlockEntityBase.ItemHandlerProvider]] /
 *    [[li.cil.oc.common.tileentity.BlockEntityBase.FluidHandlerProvider]] 暴露能力。
 */
class RobotProxy(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Computer
    with traits.PowerInformation
    with internal.Robot
    with BlockEntityBase.ItemHandlerProvider
    with BlockEntityBase.FluidHandlerProvider
    with InventorySelection
    with TankSelection {

  // ----------------------------------------------------------------------- //

  /**
   * 代理自身的节点。
   *
   * 原 1.7.10 实现在这里创建带 `robot` 组件的节点，并在 `validate()` 时把内层机器节点的
   * 地址复制过来（这样外面看到的地址与内部机器一致）。机器层（`api.Machine`）尚未就绪时
   * `machine` 为 `null`，因此这里保留独立的节点对象，只做地址同步。
   */
  override val node: Node = api.Network.newNode(this, Visibility.Network).
    withComponent("robot", Visibility.Neighbors).
    create()

  private lazy val fakePlayerInstance: Player =
    if (getLevel.isInstanceOf[ServerLevel]) FakePlayerFactory.getMinecraft(getLevel.asInstanceOf[ServerLevel])
    else null

  /** 机器人交互使用的假玩家（原 `server.agent.Player`）。 */
  def fakePlayer: Player = fakePlayerInstance

  /**
   * 机器人本体；原 1.7.10 由外部（方块 `moving` 钩子）注入，这里由代理创建。
   *
   * 使用 `lazy val` 是因为它必须在 `BlockEntity` 构造完成（`getLevel` 可用）之后才能创建，
   * 而 [[initialize]]（原 `validate()`）在区块加载时才会首次访问它。
   */
  lazy val robot: Robot = {
    val r = new Robot(getLevel, pos, state)
    r.proxy = this
    r
  }

  // ----------------------------------------------------------------------- //

  override def tier = robot.tier

  override def equipmentInventory: net.neoforged.neoforge.items.IItemHandler = robot.equipmentInventory

  override def mainInventory: net.neoforged.neoforge.items.IItemHandler = robot.mainInventory

  override def tank: internal.MultiTank = robot.tank

  override def selectedSlot: Int = robot.selectedSlot

  override def selectedSlot_=(value: Int): Unit = robot.selectedSlot = value

  override def setSelectedSlot(index: Int): Unit = robot.setSelectedSlot(index)

  override def selectedTank: Int = robot.selectedTank

  override def selectedTank_=(value: Int): Unit = robot.selectedTank = value

  override def setSelectedTank(index: Int): Unit = robot.setSelectedTank(index)

  override def player: Player = robot.player

  override def name: String = robot.name

  override def setName(name: String): Unit = robot.setName(name)

  override def ownerName: String = robot.ownerName

  override def ownerUUID: UUID = robot.ownerUUID

  // ----------------------------------------------------------------------- //

  override def connectComponents(): Unit = {
    robot.setPosition(blockPos)
    super.connectComponents()
  }

  override def disconnectComponents(): Unit = {
    super.disconnectComponents()
  }

  override def isRunning: Boolean = robot.isRunning

  override def setRunning(value: Boolean): Unit = robot.setRunning(value)

  override def shouldAnimate(): Boolean = robot.shouldAnimate

  // ----------------------------------------------------------------------- //

  override def componentCount: Int = robot.componentCount

  override def getComponentInSlot(index: Int) = robot.getComponentInSlot(index)

  override def synchronizeSlot(slot: Int): Unit = robot.synchronizeSlot(slot)

  /**
   * 本代理上的组件槽位。
   *
   * 1.7.10 里 `RobotProxy` 自己不持有物品栏（全部数据在内部 `Robot` 上），因此这里直接把
   * 槽位判定转发给机器人本体。
   */
  override def componentSlot(address: String): Int = robot.componentSlot(address)

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function():boolean -- Starts the robot. Returns true if the state changed.""")
  def start(context: Context, args: Arguments): Array[AnyRef] =
    result(machine != null && !machine.isPaused && machine.start())

  @Callback(doc = """function():boolean -- Stops the robot. Returns true if the state changed.""")
  def stop(context: Context, args: Arguments): Array[AnyRef] =
    result(machine != null && machine.stop())

  @Callback(direct = true, doc = """function():boolean -- Returns whether the robot is running.""")
  def isRunning(context: Context, args: Arguments): Array[AnyRef] =
    result(machine != null && machine.isRunning)

  @Callback(doc = "function(name: string):string -- Sets a new name and returns the old name. Robot must not be running")
  def setName(context: Context, args: Arguments): Array[AnyRef] = {
    val oldName = robot.name
    val newName: String = args.checkString(0)
    if (machine != null && machine.isRunning) return result(Unit, "is running")
    setName(newName)
    // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotNameChange(robot)。
    markBlockForUpdate()
    result(oldName)
  }

  @Callback(doc = "function():string -- Returns the robot name.")
  def getName(context: Context, args: Arguments): Array[AnyRef] = result(robot.name)

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "network.message" && message.source != this.node) message.data match {
      case Array(packet: Packet) => robot.node.sendToReachable(message.name, packet)
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    robot.tick()
    super.tick()
  }

  override protected def initialize(): Unit = {
    super.initialize()
    // 原 1.7.10 的 `validate()`：第一个代理负责让机器人本体也进入初始化流程。
    val firstProxy = robot.proxy == null || robot.proxy == this
    robot.proxy = this
    robot.setPosition(blockPos)
    if (firstProxy) {
      robot.initialize()
    }
    if (isServer && node != null && robot.machine != null && robot.machine.node != null) {
      // Use the same address we use internally on the outside.
      val nbt = new CompoundTag()
      nbt.putString("address", robot.machine.node.address)
      node.load(nbt)
    }
  }

  override def dispose(): Unit = {
    super.dispose()
    if (robot.proxy == this) {
      robot.dispose()
    }
  }

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    robot.info.load(nbt)
    super.readFromNBTForServer(nbt)
    robot.readFromNBTForServer(nbt)
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    robot.writeToNBTForServer(nbt)
  }

  /** 原 `RobotProxy.save`：全部物品栏数据都在机器人本体上，因此直接转发。 */
  def save(nbt: CompoundTag): Unit = robot.save(nbt)

  /** 原 `RobotProxy.load`：全部物品栏数据都在机器人本体上，因此直接转发。 */
  def load(nbt: CompoundTag): Unit = robot.load(nbt)

  /** 仅客户端使用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。 */
  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = robot.readFromNBTForClient(nbt)

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = robot.writeToNBTForClient(nbt)

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] =
    robot.onAnalyze(player, side, hitX, hitY, hitZ)

  // ----------------------------------------------------------------------- //

  override def isOutputEnabled: Boolean = robot.isOutputEnabled

  override def setOutputEnabled(value: Boolean): Unit = robot.setOutputEnabled(value)

  override def checkRedstoneInputChanged(): Unit = robot.checkRedstoneInputChanged()

  // ----------------------------------------------------------------------- //

  override def pitch: Direction = robot.pitch

  override def pitch_=(value: Direction): Unit = robot.pitch = value

  override def yaw: Direction = robot.yaw

  override def yaw_=(value: Direction): Unit = robot.yaw = value

  override def setFromEntityPitchAndYaw(entity: Entity): Boolean = robot.setFromEntityPitchAndYaw(entity)

  override def setFromFacing(value: Direction): Boolean = robot.setFromFacing(value)

  override def invertRotation(): Boolean = robot.invertRotation()

  override def facing: Direction = robot.facing

  override def rotate(axis: Direction): Boolean = robot.rotate(axis)

  override def toLocal(value: Direction): Direction = robot.toLocal(value)

  override def toGlobal(value: Direction): Direction = robot.toGlobal(value)

  // ----------------------------------------------------------------------- //
  // 物品栏（原 `IInventory` / `ISidedInventory` → 1.21.1 的 `IItemHandler`）

  override def getSlots: Int = robot.getSlots

  override def getStackInSlot(i: Int): ItemStack = robot.getStackInSlot(i)

  override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack =
    robot.insertItem(slot, stack, simulate)

  override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack =
    robot.extractItem(slot, amount, simulate)

  override def getSlotLimit(slot: Int): Int = robot.getSlotLimit(slot)

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = robot.isItemValid(slot, stack)

  def decrStackSize(slot: Int, amount: Int): ItemStack = robot.decrStackSize(slot, amount)

  def setInventorySlotContents(slot: Int, stack: ItemStack): Unit = robot.setInventorySlotContents(slot, stack)

  def getInventoryStackLimit: Int = robot.getInventoryStackLimit

  def getInventoryName: String = robot.getInventoryName

  def getSizeInventory: Int = robot.getSizeInventory

  def dropSlot(slot: Int, count: Int, direction: Option[Direction]): Boolean =
    robot.dropSlot(slot, count, direction)

  def dropAllSlots(): Unit = robot.dropAllSlots()

  def isItemValidForSlot(slot: Int, stack: ItemStack): Boolean = robot.isItemValidForSlot(slot, stack)

  def canExtractItem(slot: Int, stack: ItemStack, side: Int): Boolean = robot.canExtractItem(slot, stack, side)

  def canInsertItem(slot: Int, stack: ItemStack, side: Int): Boolean = robot.canInsertItem(slot, stack, side)

  def getAccessibleSlotsFromSide(side: Int): Array[Int] = robot.getAccessibleSlotsFromSide(side)

  override def itemHandler(side: Direction): net.neoforged.neoforge.items.IItemHandler =
    robot.itemHandler(side)

  // ----------------------------------------------------------------------- //

  /**
   * 是否安装了红石卡。
   *
   * TODO(integration.opencomputers): 原实现转发给 `robot.hasRedstoneCard`
   * （依赖红石卡驱动）。这里同样转发，机器人侧的判定已降级为恒 false。
   */
  override def hasRedstoneCard = robot.hasRedstoneCard

  // ----------------------------------------------------------------------- //

  override def globalBuffer: Double = robot.globalBuffer

  override def globalBuffer_=(value: Double): Unit = robot.globalBuffer = value

  override def globalBufferSize: Double = robot.globalBufferSize

  override def globalBufferSize_=(value: Double): Unit = robot.globalBufferSize = value

  // ----------------------------------------------------------------------- //
  // 流体（原 1.7.10 的 `IFluidHandler` → NeoForge 版）

  override def fluidHandler(side: Direction): IFluidHandler = robot

  override def getTanks: Int = robot.getTanks

  override def getFluidInTank(tank: Int): FluidStack = robot.getFluidInTank(tank)

  override def getTankCapacity(tank: Int): Int = robot.getTankCapacity(tank)

  override def isFluidValid(tank: Int, stack: FluidStack): Boolean = robot.isFluidValid(tank, stack)

  override def fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int = robot.fill(resource, action)

  override def drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = robot.drain(resource, action)

  override def drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = robot.drain(maxDrain, action)
}

object RobotProxy {

  /** 兼容原 `new RobotProxy()`：由方块侧通过 `createBlockEntity(pos, state)` 构造。 */
  def apply(pos: BlockPos, state: BlockState): RobotProxy = new RobotProxy(pos, state)
}
