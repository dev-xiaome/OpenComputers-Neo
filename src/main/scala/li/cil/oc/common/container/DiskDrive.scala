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
class DiskDrive(windowId: Int, playerInventory: Inventory, drive: IItemHandler)
  extends Player(windowId, MenuTypes.DiskDrive.value(), playerInventory, drive) {

  addSlotToContainer(80, 35, Slot.Floppy)
  addPlayerInventorySlots(8, 84)
}
