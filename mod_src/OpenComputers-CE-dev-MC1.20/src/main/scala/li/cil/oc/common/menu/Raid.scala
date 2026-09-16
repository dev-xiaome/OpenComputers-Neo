package li.cil.oc.common.menu

import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.blockentity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.Container
import net.minecraft.world.inventory.MenuType

class Raid(id: Int, playerInventory: Inventory, raid: Container)
  extends AbstractMenu(MenuTypes.RAID.get(), id, playerInventory, raid) {

  override protected def getHostClass = classOf[blockentity.Raid]

  addSlotToContainer(60, 23, Slot.HDD, Tier.Seven)
  addSlotToContainer(80, 23, Slot.HDD, Tier.Seven)
  addSlotToContainer(100, 23, Slot.HDD, Tier.Seven)
  addPlayerInventorySlots(8, 84)
}
