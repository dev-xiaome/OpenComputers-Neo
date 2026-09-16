package li.cil.oc.common.menu

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.blockentity
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class HoloScreen(id: Int, playerInventory: Inventory, screen: Container)
  extends AbstractMenu(MenuTypes.HOLO_SCREEN.get(), id, playerInventory, screen) {

  override protected def getHostClass = classOf[blockentity.HoloScreen]

  addSlot(new Slot(otherInventory, 0, 80, 35) {
    override def mayPlace(stack: ItemStack): Boolean =
      api.Items.get(stack) == api.Items.get(Constants.BlockName.Keyboard) && super.mayPlace(stack)

    override def getMaxStackSize: Int = 1
  })
  addPlayerInventorySlots(8, 84)
}
