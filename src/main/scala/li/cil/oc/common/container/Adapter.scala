package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory

/**
 * 适配器容器（原 1.7.10 `container.Adapter`）。
 *
 * 1.21.1 迁移：`Container` → `AbstractContainerMenu`，构造器多了 `windowId` 与 [[MenuTypes]] 里的
 * `MenuType`；宿主的 `IInventory` 由 `tileentity.Adapter` 自身充当（它实现了 `IItemHandler`）。
 */
class Adapter(windowId: Int, playerInventory: Inventory, val adapter: tileentity.Adapter)
  extends Player(windowId, MenuTypes.Adapter.value(), playerInventory, adapter) {

  addSlotToContainer(80, 35, Slot.Upgrade)
  addPlayerInventorySlots(8, 84)
}
