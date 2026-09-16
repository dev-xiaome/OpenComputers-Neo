package li.cil.oc.common.blockentity

import li.cil.oc.common.datacomponents.OCComponents

import li.cil.oc.api.datacomponents.MutableNbtComponentHolder

import java.util.UUID
import java.util.function.Consumer
import li.cil.oc.api
import li.cil.oc.api.internal
import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network._
import li.cil.oc.common.container.InventoryProxy
import li.cil.oc.common.blockentity.traits.RedstoneAware
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity
import net.minecraft.network.chat.{Component => MCComponent}
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension
import net.neoforged.neoforge.fluids.{FluidStack, IFluidTank}
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

class RobotProxy(pos: BlockPos, state: BlockState, val robot: Robot)
  extends BlockEntity(BlockEntityTypes.ROBOT.get(), pos, state)
  with traits.Computer with traits.PowerInformation with traits.RotatableBaseBlock with WorldlyContainer with IFluidHandler with internal.Robot
    with IBlockEntityExtension{

  def this(pos: BlockPos, state: BlockState) = this(pos, state, new Robot(pos, state))

  // ----------------------------------------------------------------------- //

  override val node: Component = api.Network.newNode(this, Visibility.Network).
    withComponent("robot", Visibility.Neighbors).
    create()

  override def machine: Machine = robot.machine

  override def tier: Int = robot.tier

  override def equipmentInventory: InventoryProxy {
    def inventory: Robot

    def getContainerSize: Int
  } = robot.equipmentInventory

  override def mainInventory: InventoryProxy {
    def offset: Int

    def inventory: Robot

    def getContainerSize: Int
  } = robot.mainInventory

  override def tank: MultiTank {
    def tankCount: Int

    def getFluidTank(index: Int): ManagedEnvironment with IFluidTank
  } = robot.tank

  override def selectedSlot: Int = robot.selectedSlot

  override def setSelectedSlot(index: Int): Unit = robot.setSelectedSlot(index)

  override def selectedTank: Int = robot.selectedTank

  override def setSelectedTank(index: Int): Unit = robot.setSelectedTank(index)

  override def player: entity.player.Player = robot.player()

  override def name: String = robot.name

  override def setName(name: String): Unit = robot.setName(name)

  override def ownerName: String = robot.ownerName

  override def ownerUUID: UUID = robot.ownerUUID

  // ----------------------------------------------------------------------- //

  override def connectComponents(): Unit = {}

  override def disconnectComponents(): Unit = {}

  override def isRunning: Boolean = robot.isRunning

  override def setRunning(value: Boolean): Unit = robot.setRunning(value)

  override def shouldAnimate(): Boolean = robot.shouldAnimate

  // ----------------------------------------------------------------------- //

  override def componentCount: Int = robot.componentCount

  override def getComponentInSlot(index: Int): ManagedEnvironment = robot.getComponentInSlot(index)

  override def synchronizeSlot(slot: Int): Unit = robot.synchronizeSlot(slot)

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function():boolean -- Starts the robot. Returns true if the state changed.""")
  def start(context: Context, args: Arguments): Array[AnyRef] =
    result(!machine.isPaused && machine.start())

  @Callback(doc = """function():boolean -- Stops the robot. Returns true if the state changed.""")
  def stop(context: Context, args: Arguments): Array[AnyRef] =
    result(machine.stop())

  @Callback(direct = true, doc = """function():boolean -- Returns whether the robot is running.""")
  def isRunning(context: Context, args: Arguments): Array[AnyRef] =
    result(machine.isRunning)

  @Callback(doc = "function(name: string):string -- Sets a new name and returns the old name. Robot must not be running")
  def setName(context: Context, args: Arguments): Array[AnyRef] = {
    val oldName = robot.name
    val newName: String = args.checkString(0)
    if (machine.isRunning) return result((), "is running")
    setName(newName)
    ServerPacketSender.sendRobotNameChange(robot)
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

  override def updateEntity(): Unit = {
    robot.updateEntity()
  }

  override def clearRemoved(): Unit = {
    // Modern chunk loading invokes clearRemoved after assigning the proxy's
    // level, but Computer.initialize() is called by super.clearRemoved().
    // The machine host is the real Robot, so synchronize its level/position
    // first or Machine.loadData() will see a null environment level.
    val firstProxy = robot.proxy == null
    robot.proxy = this
    robot.setLevel(getLevel)
    robot.worldPosition = getBlockPos

    super.clearRemoved()

    if (firstProxy) {
      robot.clearRemoved()
    }
    if (isServer) {
      // Computer.initialize(), invoked by super.clearRemoved(), is where the
      // deferred MachineData is finally restored on 1.21. Robot's own
      // loadComponentsForServer() ran earlier while the machine still looked
      // stopped, so its legacy onRobotStart() registration could not fire.
      //
      // Robots intentionally do not tick their Machine from updateEntity();
      // EventHandler.runningRobots owns that tick. Re-register a restored
      // running robot here, after the machine state actually exists.
      if (robot.machine.isRunning) {
        li.cil.oc.common.EventHandler.onRobotStart(robot)
      }

      // Both proxy and real Robot now have a live level; ensure every virtual
      // component environment is on the machine network before rebuilding
      // hardware bookkeeping.
      robot.connectComponents()
      robot.machine.onHostChanged()
      // Use the same address internally and externally. Node.loadData() in the
      // 1.21 port reads OCComponents.ADDRESS from a DataComponentHolder; the
      // old raw NBT "address" key is ignored.
      val addressHolder = new MutableNbtComponentHolder()
      addressHolder.set(OCComponents.ADDRESS.get(), robot.node.address)
      node.loadData(addressHolder)
    }
  }

  override def dispose(): Unit = {
    super.dispose()
    if (robot.proxy == this) {
      robot.dispose()
    }
  }

  override def loadComponentsCommon(holder: DataComponentHolder): Unit = {
    // Preserve the original 1.12 load order: RobotData contains the installed
    // hardware list and determines the robot's dynamic component-slot layout,
    // so it must be restored before the proxy superclass reconstructs
    // inventory/component state.
    robot.loadComponentsCommon(holder)
    super.loadComponentsCommon(holder)
  }

  override def saveComponentsCommon(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsCommon(holder)
    // Robot component slots are virtual and live in RobotData.info.components.
    // Persist them after their ManagedEnvironments have saved node state back
    // into the ItemStacks, so addresses survive a world save/reload.
    robot.saveComponentsCommon(holder)
  }

  override def loadComponentsForServer(holder: DataComponentHolder): Unit = {
    super.loadComponentsForServer(holder)

    // RobotProxy delegates Inventory persistence to the real Robot, but no
    // superclass server hook invokes RobotProxy.loadData(holder). Restore the
    // ordinary backing inventory explicitly from the persistent CONTENTS
    // component after RobotData has already established the dynamic slot layout.
    robot.loadData(holder)

    // Do NOT create ManagedEnvironments here. Minecraft calls this while the
    // BlockEntity is still being deserialized and before its Level is attached.
    robot.loadComponentsForServer(holder)
  }

  override def saveComponentsForServer(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForServer(holder)

    // Persist the robot's ordinary backing inventory into THIS holder. This is
    // the MutableNbtComponentHolder BaseBlockEntity serializes into chunk NBT.
    // Previously Robot.saveData() only ran against a temporary Persistable
    // holder, so CONTENTS vanished before the chunk was written.
    robot.saveData(holder)

    // Capture installed component stacks and robot-specific server state.
    robot.saveComponentsCommon(holder)
    robot.saveComponentsForServer(holder)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = robot.saveData(holder)

  override def loadData(holder: DataComponentHolder): Unit = robot.loadData(holder)

  override def loadComponentsForClient(holder: DataComponentHolder): Unit = robot.loadComponentsForClient(holder)

  override def saveComponentsForClient(holder: MutableDataComponentHolder): Unit = robot.saveComponentsForClient(holder)

  override def setChanged(): Unit = robot.setChanged()

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: entity.player.Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = robot.onAnalyze(player, side, hitX, hitY, hitZ)

  // ----------------------------------------------------------------------- //

  override protected[blockentity] val _input: Array[Int] = robot._input

  override protected[blockentity] val _output: Array[Int] = robot._output

  override protected[blockentity] val _bundledInput: Array[Array[Int]] = robot._bundledInput

  override protected[blockentity] val _rednetInput: Array[Array[Int]] = robot._rednetInput

  override protected[blockentity] val _bundledOutput: Array[Array[Int]] = robot._bundledOutput

  override def isOutputEnabled: Boolean = robot.isOutputEnabled

  override def setOutputEnabled(value: Boolean): RedstoneAware = robot.setOutputEnabled(value)

  override def checkRedstoneInputChanged(): Unit = robot.checkRedstoneInputChanged()

  // ----------------------------------------------------------------------- //

  override def pitch: Direction = robot.pitch

  override def pitch_=(value: Direction): Unit = robot.pitch_=(value)

  override def yaw: Direction = robot.yaw

  override def yaw_=(value: Direction): Unit = robot.yaw_=(value)

  override def setFromEntityPitchAndYaw(entity: Entity): Boolean = robot.setFromEntityPitchAndYaw(entity)

  override def setFromFacing(value: Direction): Boolean = robot.setFromFacing(value)

  override def invertRotation(): Boolean = robot.invertRotation()

  override def facing: Direction = robot.facing

  override def rotate(axis: Direction): Boolean = robot.rotate(axis)

  override def toLocal(value: Direction): Direction = robot.toLocal(value)

  override def toGlobal(value: Direction): Direction = robot.toGlobal(value)

  // ----------------------------------------------------------------------- //

  override def getItem(i: Int): ItemStack = robot.getItem(i)

  override def removeItem(slot: Int, amount: Int): ItemStack = robot.removeItem(slot, amount)

  override def setItem(slot: Int, stack: ItemStack): Unit = robot.setItem(slot, stack)

  override def removeItemNoUpdate(slot: Int): ItemStack = robot.removeItemNoUpdate(slot)

  override def startOpen(player: entity.player.Player): Unit = robot.startOpen(player)

  override def stopOpen(player: entity.player.Player): Unit = robot.stopOpen(player)

  override def hasCustomName: Boolean = robot.hasCustomName

  override def stillValid(player: entity.player.Player): Boolean = robot.stillValid(player)

  override def forAllLoot(dst: Consumer[ItemStack]): Unit = robot.forAllLoot(dst)

  override def dropSlot(slot: Int, count: Int, direction: Option[Direction]): Boolean = robot.dropSlot(slot, count, direction)

  override def dropAllSlots(): Unit = robot.dropAllSlots()

  override def getMaxStackSize: Int = robot.getMaxStackSize

  override def componentSlot(address: String): Int = robot.componentSlot(address)

  override def getName: MCComponent = robot.getName

  override def getContainerSize: Int = robot.getContainerSize

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean = robot.canPlaceItem(slot, stack)

  // ----------------------------------------------------------------------- //

  override def canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean = robot.canTakeItemThroughFace(slot, stack, side)

  override def canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean = robot.canPlaceItemThroughFace(slot, stack, side)

  override def getSlotsForFace(side: Direction): Array[Int] = robot.getSlotsForFace(side)

  // ----------------------------------------------------------------------- //

  override def hasRedstoneCard: Boolean = robot.hasRedstoneCard

  // ----------------------------------------------------------------------- //

  override def globalBuffer: Double = robot.globalBuffer

  override def globalBuffer_=(value: Double): Unit = robot.globalBuffer = value

  override def globalBufferSize: Double = robot.globalBufferSize

  override def globalBufferSize_=(value: Double): Unit = robot.globalBufferSize = value

  // ----------------------------------------------------------------------- //

  override def getTanks: Int = robot.getTanks

  override def getFluidInTank(tank: Int): FluidStack = robot.getFluidInTank(tank)

  override def getTankCapacity(tank: Int): Int = robot.getTankCapacity(tank)

  override def isFluidValid(tank: Int, resource: FluidStack): Boolean = robot.isFluidValid(tank, resource)

  override def fill(resource: FluidStack, action: FluidAction): Int = robot.fill(resource, action)

  override def drain(resource: FluidStack, action: FluidAction): FluidStack = robot.drain(resource, action)

  override def drain(maxDrain: Int, action: FluidAction): FluidStack = robot.drain(maxDrain, action)
}
