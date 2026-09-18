package li.cil.oc.server.component

import li.cil.oc.api.component.{RackBusConnectable, RackMountable}
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.api.{Driver, ImmutableItemStack}
import li.cil.oc.common.container.{ComponentInventory, ItemStackInventory}
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.menu.{DiskDrive => DiskDriveContainer}
import li.cil.oc.common.{Slot, Sound}
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.util.{BlockPosition, InventoryUtils}
import li.cil.oc.{Constants, api}
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.network.chat
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.{InteractionHand, MenuProvider}
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.MutableDataComponentHolder

import java.util
import scala.collection.convert.ImplicitConversionsToJava._

class DiskDriveMountable(val rack: api.internal.Rack, val slot: Int) 
  extends AbstractManagedEnvironment with ItemStackInventory with ComponentInventory with RackMountable with Analyzable with DeviceInfo with MenuProvider {
  // Stored for filling data packet when queried.
  var lastAccess = 0L

  def filesystemNode: Option[Node] = componentSlots(0) match {
    case Some(environment) => Option(environment.node)
    case _ => None
  }

  // ----------------------------------------------------------------------- //
  // DeviceInfo

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Disk,
    DeviceAttribute.Description -> "Floppy disk drive",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "RackDrive 100 Rev. 2"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  // ----------------------------------------------------------------------- //
  // Environment

  override val node: Component = api.Network.newNode(this, Visibility.Network).
    withComponent("disk_drive").
    create()

  @Callback(doc = """function():boolean -- Checks whether some medium is currently in the drive.""")
  def isEmpty(context: Context, args: Arguments): Array[AnyRef] = {
    result(filesystemNode.isEmpty)
  }

  @Callback(doc = """function([velocity:number]):boolean -- Eject the currently present medium from the drive.""")
  def eject(context: Context, args: Arguments): Array[AnyRef] = {
    val velocity = args.optDouble(0, 0) max 0 min 1
    val ejected = removeItem(0, 1)
    if (!ejected.isEmpty) {
      val entity = InventoryUtils.spawnStackInWorld(BlockPosition(rack), ejected, Option(rack.facing))
      if (entity != null) {
        val vx = rack.facing.getStepX * velocity
        val vy = rack.facing.getStepY * velocity
        val vz = rack.facing.getStepZ * velocity
        entity.push(vx, vy, vz)
      }
      result(true)
    }
    else result(false)
  }

  @Callback(doc = "function(): string -- Return the internal floppy disk address")
  def media(context: Context, args: Arguments): Array[AnyRef] = {
    if (filesystemNode.isEmpty)
      result((), "drive is empty")
    else
      result(filesystemNode.head.address)
  }

  // ----------------------------------------------------------------------- //
  // Analyzable

  override def onAnalyze(player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = filesystemNode.fold(null: Array[Node])(Array(_))

  // ----------------------------------------------------------------------- //
  // ItemStackInventory

  override def host: EnvironmentHost = rack

  // ----------------------------------------------------------------------- //
  // IInventory

  override def getContainerSize: Int = 1

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack))) match {
    case (0, Some(driver)) => driver.slot(stack) == Slot.Floppy
    case _ => false
  }

  override def stillValid(player: Player): Boolean = rack.stillValid(player)

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override def container: ItemStack = rack.getItem(slot)

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    componentSlots(slot) match {
      case Some(environment) => environment.node match {
        case component: Component => component.setVisibility(Visibility.Network)
      }
      case _ =>
    }
    if (!rack.getEnvironmentLevel.isClientSide) {
      rack.markChanged(this.slot)
      Sound.playDiskInsert(rack)
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (!rack.getEnvironmentLevel.isClientSide) {
      rack.markChanged(this.slot)
      Sound.playDiskEject(rack)
    }
  }

  // ----------------------------------------------------------------------- //
  // ManagedEnvironment

  override def canUpdate: Boolean = false

  // ----------------------------------------------------------------------- //
  // Persistable

  override def loadData(holder: DataComponentHolder): Unit = {
    super[AbstractManagedEnvironment].loadData(holder)
    super[ComponentInventory].loadData(holder)
    connectComponents()

    this.lastAccess = holder.getComponent(OCComponents.Network.LAST_ACCESS) getOrElse 0
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super[AbstractManagedEnvironment].saveData(holder)
    super[ComponentInventory].saveData(holder)
  }

  // ----------------------------------------------------------------------- //
  // RackMountable

  override def describeForClient(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.Network.LAST_ACCESS, lastAccess)
    holder.setComponent(OCComponents.Network.DISK_ITEM, ImmutableItemStack.copyOf(getItem(0)))
  }

  override def getConnectableCount: Int = 0

  override def getConnectableAt(index: Int): RackBusConnectable = null

  override def onActivate(player: Player, hand: InteractionHand, heldItem: ItemStack, hitX: Float, hitY: Float): Boolean = {
    if (player.isCrouching) {
      val isDiskInDrive = !getItem(0).isEmpty
      val isHoldingDisk = canPlaceItem(0, heldItem)
      if (isDiskInDrive) {
        if (!rack.getEnvironmentLevel.isClientSide) {
          InventoryUtils.dropSlot(BlockPosition(rack), this, 0, 1, Option(rack.facing))
        }
      }
      if (isHoldingDisk) {
        // Insert the disk.
        setItem(0, player.inventory.removeItem(player.inventory.selected, 1))
      }
      isDiskInDrive || isHoldingDisk
    }
    else player match {
      case srvPlr: ServerPlayer => {
        srvPlr.openMenu(this)
        true
      }
      case _ => false
    }
  }

  // ----------------------------------------------------------------------- //
  // INamedContainerProvider

  override def getDisplayName = chat.Component.empty

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new DiskDriveContainer(id, playerInventory, this)

  // ----------------------------------------------------------------------- //
  // StateAware

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = util.EnumSet.noneOf(classOf[api.util.StateAware.State])
}
