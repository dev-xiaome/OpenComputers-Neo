package li.cil.oc.common.blockentity

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.Sound
import li.cil.oc.common.menu
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.MenuProvider
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import scala.jdk.CollectionConverters._

class DiskDrive(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.DISK_DRIVE.get(), pos, state) with traits.Environment
  with traits.ComponentInventory with traits.Rotatable with Analyzable with DeviceInfo with MenuProvider
    with IBlockEntityExtension{

  // Used on client side to check whether to render disk activity indicators.
  var lastAccess = 0L

  def filesystemNode: Option[Node] = componentSlots(0) match {
    case Some(environment) => Option(environment.node)
    case _ => None
  }

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Disk,
    DeviceAttribute.Description -> "Floppy disk drive",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Spinner 520p1"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //
  // Environment

  val node: Component = api.Network.newNode(this, Visibility.Network).
    withComponent("disk_drive").
    create()

  @Callback(doc = "function():boolean -- Checks whether some medium is currently in the drive.")
  def isEmpty(context: Context, args: Arguments): Array[AnyRef] = {
    result(filesystemNode.isEmpty)
  }

  @Callback(doc = "function([velocity:number]):boolean -- Eject the currently present medium from the drive.")
  def eject(context: Context, args: Arguments): Array[AnyRef] = {
    val velocity = args.optDouble(0, 0) max 0 min 1
    val ejected = removeItem(0, 1)
    if (!ejected.isEmpty) {
      val entity = InventoryUtils.spawnStackInWorld(position, ejected, Option(facing))
      if (entity != null) {
        val vx = facing.getStepX * velocity
        val vy = facing.getStepY * velocity
        val vz = facing.getStepZ * velocity
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

  override def onAnalyze(player: PlayerEntity, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = filesystemNode.fold(null: Array[Node])(Array(_))

  // ----------------------------------------------------------------------- //
  // IInventory

  override def getContainerSize = 1

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (0, Some(driver)) => driver.slot(stack) == Slot.Floppy
    case _ => false
  }

  // ----------------------------------------------------------------------- //
  // INamedContainerProvider

  override def createMenu(id: Int, playerInventory: Inventory, player: PlayerEntity) =
    new menu.DiskDrive(id, playerInventory, this)

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    componentSlots(slot) match {
      case Some(environment) => environment.node match {
        case component: Component => component.setVisibility(Visibility.Network)
      }
      case _ =>
    }
    if (isServer) {
      ServerPacketSender.sendFloppyChange(this, stack)
      Sound.playDiskInsert(this)
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (isServer) {
      ServerPacketSender.sendFloppyChange(this)
      Sound.playDiskEject(this)
    }
  }
}
