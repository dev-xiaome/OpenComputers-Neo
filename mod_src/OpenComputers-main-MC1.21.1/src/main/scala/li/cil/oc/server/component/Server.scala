package li.cil.oc.server.component

import li.cil.oc.{Constants, api}
import li.cil.oc.api.component.RackBusConnectable
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.Persistable
import li.cil.oc.api.{Machine, internal}
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.{Analyzable, Environment, Message, Node}
import li.cil.oc.common.container.{ComponentInventory, ServerInventory}
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.{InventorySlots, Slot, Tier, item}
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.server.network.Connector
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.Direction
import net.minecraft.core.component.{DataComponentHolder, DataComponentMap}
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.MutableDataComponentHolder

import java.util
import scala.collection.convert.ImplicitConversionsToJava._

class Server(val rack: api.internal.Rack, val slot: Int) extends Environment with MachineHost with ServerInventory with ComponentInventory with Analyzable with internal.Server with DeviceInfo {
  lazy val machine: api.machine.Machine = Machine.create(this)

  val node: Node = if (!rack.getEnvironmentLevel.isClientSide) machine.node else null

  var wasRunning = false
  var hadErrored = false
  var lastFileSystemAccess = 0L
  var lastNetworkActivity = 0L
  private var pendingMachineData: DataComponentMap = null

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.System,
    DeviceAttribute.Description -> "Server",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Blader",
    DeviceAttribute.Capacity -> getContainerSize.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  // ----------------------------------------------------------------------- //
  // Environment

  override def onConnect(node: Node): Unit = {
    if (node == this.node) {
      connectComponents()
    }
  }

  override def onDisconnect(node: Node): Unit = {
    if (node == this.node) {
      disconnectComponents()
    }
  }

  override def onMessage(message: Message): Unit = {
  }

  override def loadData(holder: DataComponentHolder): Unit = {
    super.loadData(holder)
    if(!rack.getEnvironmentLevel.isClientSide) {
      // Unlike a normal computer, a server is reconstructed as a component of
      // its rack. Defer restoring its VM until the first live rack update so
      // neighboring block entities (in particular a bound screen and its
      // external buffer) have completed their own onLoad lifecycle.
      pendingMachineData = holder.getComponents
    }
  }

  private def loadPendingMachineData(): Unit = {
    if (pendingMachineData != null) {
      val data = pendingMachineData
      pendingMachineData = null
      machine.loadData(Persistable.holder(data))
    }
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    if(!rack.getEnvironmentLevel.isClientSide) {
      // Saving may happen before the rack receives its first update (for
      // example when quitting immediately after loading). Never replace the
      // deferred machine snapshot with a newly constructed stopped machine.
      loadPendingMachineData()
    }
    super.saveData(holder)
    if(!rack.getEnvironmentLevel.isClientSide) {
      machine.saveData(holder)
    }
  }

  // ----------------------------------------------------------------------- //
  // MachineHost

  override def internalComponents(): java.lang.Iterable[ItemStack] = (0 until getContainerSize).collect {
    case i if !getItem(i).isEmpty && isComponentSlot(i, getItem(i)) => getItem(i)
  }

  override def componentSlot(address: String): Int = componentSlots.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  override def onMachineConnect(node: Node): Unit = onConnect(node)

  override def onMachineDisconnect(node: Node): Unit = onDisconnect(node)

  // ----------------------------------------------------------------------- //
  // EnvironmentHost

  override def xPosition: Double = rack.xPosition

  override def yPosition: Double = rack.yPosition

  override def zPosition: Double = rack.zPosition

  override def getEnvironmentLevel: Level = rack.getEnvironmentLevel

  override def markChanged(): Unit = rack.markChanged()

  // ----------------------------------------------------------------------- //
  // ServerInventory

  override def rackSlot = slot

  override def tier: Int = container.getItem match {
    case server: item.Server => server.tier
    case _ => 0
  }

  override def stillValid(player: Player): Boolean = rack.stillValid(player) && rack.indexOfMountable(this) >= 0

  // ----------------------------------------------------------------------- //
  // ItemStackInventory

  override def host: Rack = rack

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override def container: ItemStack = rack.getItem(slot)

  override protected def connectItemNode(node: Node): Unit = {
    if (node != null) {
      api.Network.joinNewNetwork(machine.node)
      machine.node.connect(node)
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (!rack.getEnvironmentLevel.isClientSide) {
      val slotType = InventorySlots.server(tier)(slot).slot
      if (slotType == Slot.CPU) {
        machine.stop()
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // RackMountable

  override def describeForClient(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.IS_RUNNING, wasRunning)
    holder.setComponent(OCComponents.IS_ERRORED, hadErrored)
    holder.setComponent(OCComponents.Network.LAST_DISK_ACCESS, lastFileSystemAccess)
    holder.setComponent(OCComponents.Network.LAST_NETWORK_ACCESS, lastNetworkActivity)
  }

  override def getConnectableCount: Int = componentSlots.count {
    case Some(_: RackBusConnectable) => true
    case _ => false
  }

  override def getConnectableAt(index: Int): RackBusConnectable = componentSlots.collect {
    case Some(busConnectable: RackBusConnectable) => busConnectable
  }.apply(index)

  override def onActivate(player: Player, hand: InteractionHand, heldItem: ItemStack, hitX: Float, hitY: Float): Boolean = {
    if (!player.level.isClientSide) {
      if (player.isCrouching) {
        if (!machine.isRunning && stillValid(player)) {
          wasRunning = false
          hadErrored = false
          machine.start()
        }
      }
      else {
        player match {
          case srvPlr: ServerPlayer => MenuTypes.openServerGui(srvPlr, this, slot)
          case _ =>
        }
      }
    }
    true
  }

  // ----------------------------------------------------------------------- //
  // ManagedEnvironment

  override def canUpdate: Boolean = true

  override def update(): Unit = {
    if (!rack.getEnvironmentLevel.isClientSide) {
      loadPendingMachineData()
      machine.update()

      val isRunning = machine.isRunning
      val hasErrored = machine.lastError != null
      if (isRunning != wasRunning || hasErrored != hadErrored) {
        rack.markChanged(slot)
      }
      wasRunning = isRunning
      hadErrored = hasErrored
      if (tier == Tier.Five) node.asInstanceOf[Connector].changeBuffer(Double.PositiveInfinity)
    }

    updateComponents()
  }

  // ----------------------------------------------------------------------- //
  // StateAware

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    if (machine.isRunning) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //
  // Analyzable

  override def onAnalyze(player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = Array(machine.node)
}
