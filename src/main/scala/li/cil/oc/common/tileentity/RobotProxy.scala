package li.cil.oc.common.tileentity

import net.neoforged.fml.common.Optional
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.api
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.integration.Mods
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import mods.immibis.redlogic.api.wiring.IWire
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.inventory.ISidedInventory
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

class RobotProxy(val robot: Robot) extends traits.Computer with traits.PowerInformation with ISidedInventory with IFluidHandler with internal.Robot {
  def this() = this(new Robot())

  // ----------------------------------------------------------------------- //

  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("robot", Visibility.Neighbors).
    create()

  override def machine = robot.machine

  override def tier = robot.tier

  override def equipmentInventory = robot.equipmentInventory

  override def mainInventory = robot.mainInventory

  override def tank = robot.tank

  override def selectedSlot = robot.selectedSlot

  override def setSelectedSlot(index: Int) = robot.setSelectedSlot(index)

  override def selectedTank = robot.selectedTank

  override def setSelectedTank(index: Int) = robot.setSelectedTank(index)

  override def player = robot.player()

  override def name = robot.name

  override def setName(name: String): Unit = robot.setName(name)

  override def ownerName = robot.ownerName

  override def ownerUUID = robot.ownerUUID

  // ----------------------------------------------------------------------- //

  override def connectComponents() {}

  override def disconnectComponents() {}

  override def isRunning = robot.isRunning

  override def setRunning(value: Boolean) = robot.setRunning(value)

  override def shouldAnimate(): Boolean = robot.shouldAnimate

  // ----------------------------------------------------------------------- //

  override def componentCount = robot.componentCount

  override def getComponentInSlot(index: Int) = robot.getComponentInSlot(index)

  override def synchronizeSlot(slot: Int) = robot.synchronizeSlot(slot)

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
    if (machine.isRunning) return result(Unit, "is running")
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

  override def validate(): Unit = {
    super.validate()
    val firstProxy = robot.proxy == null
    robot.proxy = this
    robot.setWorldObj(worldObj)
    robot.xCoord = xCoord
    robot.yCoord = yCoord
    robot.zCoord = zCoord
    if (firstProxy) {
      robot.validate()
    }
    if (isServer) {
      // Use the same address we use internally on the outside.
      val nbt = new CompoundTag()
      nbt.putString("address", robot.node.address)
      node.load(nbt)
    }
  }

  override def dispose(): Unit = {
    super.dispose()
    if (robot.proxy == this) {
      robot.dispose()
    }
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    robot.info.load(nbt)
    super.readFromNBTForServer(nbt)
    robot.readFromNBTForServer(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    robot.writeToNBTForServer(nbt)
  }

  override def save(nbt: CompoundTag) = robot.save(nbt)

  override def load(nbt: CompoundTag) = robot.load(nbt)

  @SideOnly(Dist.CLIENT)
  override def readFromNBTForClient(nbt: CompoundTag) = robot.readFromNBTForClient(nbt)

  override def writeToNBTForClient(nbt: CompoundTag) = robot.writeToNBTForClient(nbt)

  override def getMaxRenderDistanceSquared = robot.getMaxRenderDistanceSquared

  override def getRenderBoundingBox = robot.getRenderBoundingBox

  override def shouldRenderInPass(pass: Int) = robot.shouldRenderInPass(pass)

  override def markDirty() = robot.markDirty()

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float) = robot.onAnalyze(player, side, hitX, hitY, hitZ)

  // ----------------------------------------------------------------------- //

  override protected[tileentity] val _input = robot._input

  override protected[tileentity] val _output = robot._output

  override protected[tileentity] val _bundledInput = robot._bundledInput

  override protected[tileentity] val _rednetInput = robot._rednetInput

  override protected[tileentity] val _bundledOutput = robot._bundledOutput

  override def isOutputEnabled: Boolean = robot.isOutputEnabled

  override def setOutputEnabled(value: Boolean): Unit = robot.setOutputEnabled(value)

  override def checkRedstoneInputChanged() = robot.checkRedstoneInputChanged()

  @Optional.Method(modid = Mods.IDs.RedLogic)
  override def connects(wire: IWire, blockFace: Int, fromDirection: Int) = robot.connects(wire, blockFace, fromDirection)

  @Optional.Method(modid = Mods.IDs.RedLogic)
  override def connectsAroundCorner(wire: IWire, blockFace: Int, fromDirection: Int) = robot.connectsAroundCorner(wire, blockFace, fromDirection)

  @Optional.Method(modid = Mods.IDs.RedLogic)
  override def getBundledCableStrength(blockFace: Int, toDirection: Int) = robot.getBundledCableStrength(blockFace, toDirection)

  @Optional.Method(modid = Mods.IDs.RedLogic)
  override def getEmittedSignalStrength(blockFace: Int, toDirection: Int) = robot.getEmittedSignalStrength(blockFace, toDirection)

  @Optional.Method(modid = Mods.IDs.RedLogic)
  override def onBundledInputChanged() = robot.onBundledInputChanged()

  @Optional.Method(modid = Mods.IDs.RedLogic)
  override def onRedstoneInputChanged() = robot.onRedstoneInputChanged()

  // ----------------------------------------------------------------------- //

  override def pitch = robot.pitch

  override def pitch_=(value: Direction) = robot.pitch_=(value)

  override def yaw = robot.yaw

  override def yaw_=(value: Direction) = robot.yaw_=(value)

  override def setFromEntityPitchAndYaw(entity: Entity) = robot.setFromEntityPitchAndYaw(entity)

  override def setFromFacing(value: Direction) = robot.setFromFacing(value)

  override def invertRotation() = robot.invertRotation()

  override def facing = robot.facing

  override def rotate(axis: Direction) = robot.rotate(axis)

  override def toLocal(value: Direction) = robot.toLocal(value)

  override def toGlobal(value: Direction) = robot.toGlobal(value)

  // ----------------------------------------------------------------------- //

  override def getStackInSlot(i: Int) = robot.getStackInSlot(i)

  override def decrStackSize(slot: Int, amount: Int) = robot.decrStackSize(slot, amount)

  override def setInventorySlotContents(slot: Int, stack: ItemStack) = robot.setInventorySlotContents(slot, stack)

  override def getStackInSlotOnClosing(slot: Int) = robot.getStackInSlotOnClosing(slot)

  override def openInventory() = robot.openInventory()

  override def closeInventory() = robot.closeInventory()

  override def hasCustomInventoryName = robot.hasCustomInventoryName

  override def isUseableByPlayer(player: Player) = robot.isUseableByPlayer(player)

  override def dropSlot(slot: Int, count: Int, direction: Option[Direction]) = robot.dropSlot(slot, count, direction)

  override def dropAllSlots() = robot.dropAllSlots()

  override def getInventoryStackLimit = robot.getInventoryStackLimit

  override def componentSlot(address: String) = robot.componentSlot(address)

  override def getInventoryName = robot.getInventoryName

  override def getSizeInventory = robot.getSizeInventory

  override def isItemValidForSlot(slot: Int, stack: ItemStack) = robot.isItemValidForSlot(slot, stack)

  // ----------------------------------------------------------------------- //

  override def canExtractItem(slot: Int, stack: ItemStack, side: Int) = robot.canExtractItem(slot, stack, side)

  override def canInsertItem(slot: Int, stack: ItemStack, side: Int) = robot.canInsertItem(slot, stack, side)

  override def getAccessibleSlotsFromSide(side: Int) = robot.getAccessibleSlotsFromSide(side)

  // ----------------------------------------------------------------------- //

  override def hasRedstoneCard = robot.hasRedstoneCard

  // ----------------------------------------------------------------------- //

  override def globalBuffer = robot.globalBuffer

  override def globalBuffer_=(value: Double) = robot.globalBuffer = value

  override def globalBufferSize = robot.globalBufferSize

  override def globalBufferSize_=(value: Double) = robot.globalBufferSize = value

  // ----------------------------------------------------------------------- //

  override def fill(from: Direction, resource: FluidStack, doFill: Boolean) = robot.fill(from, resource, doFill)

  override def drain(from: Direction, resource: FluidStack, doDrain: Boolean) = robot.drain(from, resource, doDrain)

  override def drain(from: Direction, maxDrain: Int, doDrain: Boolean) = robot.drain(from, maxDrain, doDrain)

  override def canFill(from: Direction, fluid: Fluid) = robot.canFill(from, fluid)

  override def canDrain(from: Direction, fluid: Fluid) = robot.canDrain(from, fluid)

  override def getTankInfo(from: Direction) = robot.getTankInfo(from)
}
