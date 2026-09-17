package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class ShulkerBoxSlot extends Slot {
    public ShulkerBoxSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    /**
     * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        // Neo: stack-aware placeability check
        return stack.canFitInsideContainerItems();
    }
}
