package li.cil.oc.server.component

import java.util

import li.cil.oc.{Constants, api}
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.Driver
import li.cil.oc.api.component.RackBusConnectable
import li.cil.oc.api.component.RackMountable
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Analyzable
// 注意：不能写 `import li.cil.oc.api.network.Component`，否则会与
// `net.minecraft.network.chat.Component`（`MenuOpening` 的标题参数）重名冲突，
// 本文件里凡是 `api.network.Component` 都写成全限定名。
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.common.{GuiType, Slot, Sound}
import li.cil.oc.common.container.MenuOpening
import li.cil.oc.common.inventory.ComponentInventory
import li.cil.oc.common.inventory.ItemStackInventory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.InventoryUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

import scala.jdk.CollectionConverters._

/**
 * 机架上可插拔的软驱（对应 1.7.10 的 `server.component.DiskDriveMountable`）。
 *
 * ==1.21.1 迁移要点==
 *  - `IInventory` → `IItemHandler`：`getSizeInventory` → `getSlots`（由
 *    [[li.cil.oc.common.inventory.ItemStackInventory]] 的 `getSlots` 提供），
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `stack.stackSize` → `stack.getCount`；`Direction.offsetX/Y/Z` → `getStepX/Y/Z`；
 *    `Entity#addVelocity` → `Entity#setDeltaMovement`。
 *  - `world.isRemote` → `world.isClientSide`；`player.isSneaking` → `player.isShiftKeyDown`；
 *    `player.getHeldItem`（无参）→ `player.getMainHandItem`；
 *    `player.inventory` → `player.getInventory`。
 *  - `player.openGui(...)` → `player.openMenu(MenuProvider)`，见 [[onActivate]] 的 TODO。
 */
class DiskDriveMountable(val rack: api.internal.Rack, val slot: Int) extends prefab.ManagedEnvironment with ItemStackInventory with ComponentInventory with RackMountable with Analyzable with DeviceInfo {
  // Stored for filling data packet when queried.
  var lastAccess = 0L

  def filesystemNode = componentEnvironments(0) match {
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

  // 1.21.1：`DeviceInfo#getDeviceInfo` 返回 `java.util.Map`，Scala 的 `Map` 需要显式转换。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //
  // Environment

  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("disk_drive").
    create()

  @Callback(doc = """function():boolean -- Checks whether some medium is currently in the drive.""")
  def isEmpty(context: Context, args: Arguments): Array[AnyRef] = {
    result(filesystemNode.isEmpty)
  }

  @Callback(doc = """function([velocity:number]):boolean -- Eject the currently present medium from the drive.""")
  def eject(context: Context, args: Arguments): Array[AnyRef] = {
    val velocity = args.optDouble(0, 0) max 0 min 1
    val ejected = decrStackSize(0, 1)
    // 1.21.1：`stack.stackSize` → `stack.getCount`（`decrStackSize` 仍然把空堆叠还原成 `null`）。
    if (ejected != null && ejected.getCount > 0) {
      val entity = InventoryUtils.spawnStackInWorld(BlockPosition(rack), ejected, Option(rack.facing))
      if (entity != null) {
        // 1.21.1：`ForgeDirection#offsetX/Y/Z` → `Direction#getStepX/getStepY/getStepZ`。
        val vx = rack.facing.getStepX * velocity
        val vy = rack.facing.getStepY * velocity
        val vz = rack.facing.getStepZ * velocity
        // 1.21.1：`Entity#addVelocity(x, y, z)` 已移除，改为设置速度向量。
        entity.setDeltaMovement(vx, vy, vz)
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

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float) = filesystemNode.fold(null: Array[Node])(Array(_))

  // ----------------------------------------------------------------------- //
  // ItemStackInventory

  override def host: EnvironmentHost = rack

  // ----------------------------------------------------------------------- //
  // IItemHandler

  override def getSlots: Int = 1

  /** 原 `isItemValidForSlot`：只有 `Slot.Floppy` 类的驱动物品（软盘）可以放进 0 号槽。 */
  override def isItemValid(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack))) match {
    case (0, Some(driver)) => driver.slot(stack) == Slot.Floppy
    case _ => false
  }

  /**
   * 原 `IInventory#isUseableByPlayer`。
   *
   * TODO(server.component/Server): `api.internal.Rack` 上**没有** `isUseableByPlayer`
   * （它只在 `common.tileentity.traits.Inventory` 里，属于方块实体实现细节，
   * 而这里拿到的是 API 接口）。因此无法再转发给机架，改为等价的距离判定
   * （与原实现最终走到的 `traits.Inventory#isUseableByPlayer` 完全相同：距离平方 ≤ 64）。
   * 注意 `server/component/Server.scala` 第 123 行有同一处未修的错误，属于别人的文件，未改动。
   */
  override def isUseableByPlayer(player: Player): Boolean =
    player.distanceToSqr(rack.xPosition, rack.yPosition, rack.zPosition) <= 64

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override def container: ItemStack = rack.getStackInSlot(slot)

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    componentEnvironments(slot) match {
      case Some(environment) => environment.node match {
        // 全限定名：本文件里 `Component` 被 `net.minecraft.network.chat.Component` 占用。
        case component: li.cil.oc.api.network.Component => component.setVisibility(Visibility.Network)
      }
      case _ =>
    }
    Sound.playDiskInsert(rack)
    // 1.21.1：`Level#isRemote` → `isClientSide`。
    if (!rack.world.isClientSide) {
      rack.markChanged(this.slot)
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    Sound.playDiskEject(rack)
    if (!rack.world.isClientSide) {
      rack.markChanged(this.slot)
    }
  }

  // ----------------------------------------------------------------------- //
  // ManagedEnvironment

  override def canUpdate: Boolean = false

  // ----------------------------------------------------------------------- //
  // Persistable

  override def load(nbt: CompoundTag): Unit = {
    super[ManagedEnvironment].load(nbt)
    super[ComponentInventory].load(nbt)
    connectComponents()
  }

  override def save(nbt: CompoundTag): Unit = {
    super[ManagedEnvironment].save(nbt)
    super[ComponentInventory].save(nbt)
  }

  // ----------------------------------------------------------------------- //
  // RackMountable

  override def getData: CompoundTag = {
    val nbt = new CompoundTag()
    nbt.putLong("lastAccess", lastAccess)
    // `toNbt` 是 `li.cil.oc.util.ExtendedNBT` 提供的隐式转换（1.21.1 的 `ItemStack`
    // 需要 `HolderLookup.Provider` 才能序列化，扩展里用 `RegistryAccess.EMPTY` 兜底）。
    nbt.put("disk", toNbt(getStackInSlot(0)))
    nbt
  }

  override def getConnectableCount: Int = 0

  override def getConnectableAt(index: Int): RackBusConnectable = null

  override def onActivate(player: Player, hitX: Float, hitY: Float): Boolean = {
    // 1.21.1：`player.isSneaking` → `player.isShiftKeyDown`。
    if (player.isShiftKeyDown) {
      val isDiskInDrive = !getStackInSlot(0).isEmpty
      // 1.21.1：`player.getHeldItem`（无参，主手）→ `player.getMainHandItem`。
      val isHoldingDisk = isItemValid(0, player.getMainHandItem)
      if (isDiskInDrive) {
        if (!rack.world.isClientSide) {
          InventoryUtils.dropSlot(BlockPosition(rack), this, 0, 1, Option(rack.facing))
        }
      }
      if (isHoldingDisk) {
        // Insert the disk.
        // 1.21.1：`player.inventory` → `player.getInventory`；
        // 主手的快捷栏下标是 `Inventory#selected`（公开字段），
        // `Inventory#decrStackSize(slot, n)`（1.7.10）已移除，等价物是
        // `Inventory#removeItem(slot, n)`（取出指定数量并就地削减，剩下少于 1 就清空）。
        setInventorySlotContents(0, player.getInventory.removeItem(player.getInventory.selected, 1))
      }
      isDiskInDrive || isHoldingDisk
    }
    else {
      // TODO(server.PacketSender / client.gui): 原实现为
      // `player.openGui(OpenComputers, GuiType.DiskDriveMountableInRack.id, rack.world, x, embedSlot(y, slot), z)`。
      // 1.21.1 没有 `openGui`：服务端用 `player.openMenu(MenuProvider)` 建立容器
      // （见 `li.cil.oc.common.container.MenuOpening`），客户端由 `MenuType` 的
      // Screen 工厂重建界面（`client` 层尚未移植，因此目前只会打开容器、没有界面）。
      // 这里直接构造与 `GuiHandler#getServerMenu` 中 `DiskDriveMountableInRack` 分支
      // 完全相同的容器，保证接线后行为一致。
      // 1.7.10 把机架坐标与插槽压进 `y`（`GuiType.embedSlot`）；1.21.1 改为把
      // 宿主坐标 + 插槽写进菜单载荷，因此改用 `MenuOpening.openRackSlot`。
      val rackPos = rack match {
        case blockEntity: net.minecraft.world.level.block.entity.BlockEntity => blockEntity.getBlockPos
        case _ => new net.minecraft.core.BlockPos(
          math.floor(rack.xPosition).toInt, math.floor(rack.yPosition).toInt, math.floor(rack.zPosition).toInt)
      }
      MenuOpening.openRackSlot(player, rackPos, slot, Component.empty())((windowId, playerInventory) =>
        new li.cil.oc.common.container.DiskDrive(windowId, playerInventory, this))
      true
    }
  }

  // ----------------------------------------------------------------------- //
  // StateAware

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = util.EnumSet.noneOf(classOf[api.util.StateAware.State])
}
