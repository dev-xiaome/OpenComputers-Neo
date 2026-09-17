package li.cil.oc.common.container

import li.cil.oc.common.Slot
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.items.IItemHandler

/**
 * 软驱容器（原 1.7.10 `container.DiskDrive`）。
 *
 * 1.21.1 迁移：`Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 * 宿主参数由 `IInventory` 放宽为 [[net.neoforged.neoforge.items.IItemHandler]] ——
 * 方块形态传 `tileentity.DiskDrive`，物品形态传 `DiskDriveMountableInventory`，两者都是 `IItemHandler`。
 */
class DiskDrive(windowId: Int, playerInventory: Inventory, val drive: IItemHandler)
  extends Player(windowId, MenuTypes.DiskDrive.value(), playerInventory, drive) {

  addSlotToContainer(80, 35, Slot.Floppy)
  addPlayerInventorySlots(8, 84)

  /**
   * 宿主的显示名（原 1.7.10 的 `drive.getInventoryName`）。
   *
   * 1.21.1 里宿主是 [[net.neoforged.neoforge.items.IItemHandler]]，它**没有**名字；
   * OC 的方块实体与幽灵物品栏都额外实现了 `common.inventory.Inventory`，
   * 名字在那一层。这里做一次类型匹配，取不到时退化成默认名。
   */
  def driveName: String = drive match {
    case inventory: li.cil.oc.common.inventory.Inventory => inventory.getInventoryName
    case _ => li.cil.oc.Settings.namespace + "container.DiskDrive"
  }
}
