package li.cil.oc.common.menu

import li.cil.oc.common.Tier
import li.cil.oc.common.blockentity
import li.cil.oc.integration.util.ItemCharge
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

class Charger(id: Int, playerInventory: Inventory, charger: Container)
  extends AbstractMenu(MenuTypes.CHARGER.get(), id, playerInventory, charger) {

  override protected def getHostClass = classOf[blockentity.Charger]

  addSlot(new StaticComponentSlot(this, otherInventory, slots.size, 80, 35, getHostClass, "tablet", Tier.Any) {
    override def mayPlace(stack: ItemStack): Boolean = {
      if (!container.canPlaceItem(getSlotIndex, stack)) return false
      ItemCharge.canCharge(stack)
    }
  })
  addPlayerInventorySlots(8, 84)
}
