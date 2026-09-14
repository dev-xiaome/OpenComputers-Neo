package li.cil.oc.common.container

import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory

/**
 * RAID 容器（原 1.7.10 `container.Raid`）。
 *
 * 1.21.1 迁移：`Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）。
 */
class Raid(windowId: Int, playerInventory: Inventory, raid: tileentity.Raid)
  extends Player(windowId, MenuTypes.Raid.value(), playerInventory, raid) {

  addSlotToContainer(60, 23, Slot.HDD, Tier.Three)
  addSlotToContainer(80, 23, Slot.HDD, Tier.Three)
  addSlotToContainer(100, 23, Slot.HDD, Tier.Three)
  addPlayerInventorySlots(8, 84)
}
