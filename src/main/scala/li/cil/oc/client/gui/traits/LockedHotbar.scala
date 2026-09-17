package li.cil.oc.client.gui.traits

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.{AbstractContainerMenu, ClickType, Slot}
import net.minecraft.world.item.ItemStack

trait LockedHotbar[C <: AbstractContainerMenu] extends AbstractContainerScreen[C] {
  def lockedStack: ItemStack

  override def slotClicked(slot: Slot, slotId: Int, mouseButton: Int, clickType: ClickType): Unit = {
    if (slot == null || !ItemStack.isSameItem(slot.getItem, lockedStack)) {
      super.slotClicked(slot, slotId, mouseButton, clickType)
    }
  }
}
