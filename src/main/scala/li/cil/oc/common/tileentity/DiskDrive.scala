package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.Sound
import li.cil.oc.util.ExtendedNBT
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.InventoryUtils
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import scala.jdk.CollectionConverters._

/**
 * 软盘驱动器（原 1.7.10 `common.tileentity.DiskDrive`）。
 *
 * 纹理：下/上 = DiskDriveTop，北 = DiskDriveFront，南 = DiskDriveBack，其它 = DiskDriveSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `getSizeInventory` → `getSlots`、`getInventoryStackLimit` → `getSlotLimit`、
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `ItemStack.loadItemStackFromNBT` → `ItemStack.parseOptional`（走
 *    [[li.cil.oc.util.ExtendedNBT.fallbackRegistry]]）；`stack.writeToNBT` → `stack.save`。
 *  - `stack.stackSize` → `stack.getCount`；`stack.isEmpty` 取代 `stack == null`。
 *  - `facing.offsetX/Y/Z` → `facing.getStepX/getStepY/getStepZ`；
 *    `ItemEntity#addVelocity` → `Entity#setDeltaMovement`。
 *  - 删除 `@SideOnly(Side.CLIENT)`（NeoForge 会因此抛异常），客户端专用分支用注释标注。
 *  - 发包降级：原 `ServerPacketSender.sendFloppyChange(...)` → `markBlockForUpdate()` + TODO。
 */
class DiskDrive(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.ComponentInventory with traits.Rotatable with Analyzable with DeviceInfo {

  // Used on client side to check whether to render disk activity indicators.
  var lastAccess = 0L

  def filesystemNode: Option[Node] = components(0) match {
    case Some(environment) => Option(environment.node)
    case _ => None
  }

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Disk,
    DeviceAttribute.Description -> "Floppy disk drive",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Spinner 520p1"
  )

  // 1.7.10 的 `scala.collection.convert.WrapAsJava._` 提供隐式转换；
  // 1.21.1（Scala 2.13）改为显式 `.asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //
  // Environment

  val node: Node = api.Network.newNode(this, Visibility.Network).
    withComponent("disk_drive").
    create()

  @Callback(doc = "function():boolean -- Checks whether some medium is currently in the drive.")
  def isEmpty(context: Context, args: Arguments): Array[AnyRef] = {
    result(filesystemNode.isEmpty)
  }

  @Callback(doc = "function([velocity:number]):boolean -- Eject the currently present medium from the drive.")
  def eject(context: Context, args: Arguments): Array[AnyRef] = {
    val velocity = args.optDouble(0, 0) max 0 min 1
    // 原 `decrStackSize(0, 1)`；1.21.1 的 `IItemHandler` 用 `extractItem`。
    val ejected = extractItem(0, 1, false)
    if (ejected != null && !ejected.isEmpty) {
      val entity = InventoryUtils.spawnStackInWorld(position, ejected, Option(facing))
      if (entity != null) {
        entity.setDeltaMovement(
          facing.getStepX * velocity,
          facing.getStepY * velocity,
          facing.getStepZ * velocity)
      }
      result(true)
    }
    else result(false)
  }

  @Callback(doc = "function(): string -- Return the internal floppy disk address")
  def media(context: Context, args: Arguments): Array[AnyRef] = {
    if (filesystemNode.isEmpty)
      result(Unit, "drive is empty")
    else
      result(filesystemNode.head.address)
  }

  // ----------------------------------------------------------------------- //
  // Analyzable

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] =
    filesystemNode.fold(null: Array[Node])(Array(_))

  // ----------------------------------------------------------------------- //
  // IItemHandler（原 IInventory）

  override def getSlots: Int = 1

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (0, Some(driver)) => driver.slot(stack) == Slot.Floppy
    case _ => false
  }

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    components(slot) match {
      case Some(environment) => environment.node match {
        case component: Component => component.setVisibility(Visibility.Network)
        case _ =>
      }
      case _ =>
    }
    Sound.playDiskInsert(this)
    if (isServer) {
      // TODO(server.PacketSender): 原为 ServerPacketSender.sendFloppyChange(this, stack)。
      // 网络层移植后改为发送 FloppyChange 包（只同步盘片物品，避免整包方块实体同步）。
      markBlockForUpdate()
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    Sound.playDiskEject(this)
    if (isServer) {
      // TODO(server.PacketSender): 原为 ServerPacketSender.sendFloppyChange(this)。
      markBlockForUpdate()
    }
  }

  // ----------------------------------------------------------------------- //
  // BlockEntity

  override def canUpdate: Boolean = false

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（该方法只在客户端读同步标签时调用）。
  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    if (nbt.contains("disk")) {
      setInventorySlotContents(0, ItemStack.parseOptional(ExtendedNBT.fallbackRegistry, nbt.getCompound("disk")))
    }
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    items(0) match {
      case Some(stack) if stack != null && !stack.isEmpty =>
        nbt.setNewCompoundTag("disk", (tag: CompoundTag) => stack.save(ExtendedNBT.fallbackRegistry, tag))
      case _ =>
    }
  }
}
