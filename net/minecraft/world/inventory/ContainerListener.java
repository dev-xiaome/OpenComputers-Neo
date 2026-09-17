package net.minecraft.world.inventory;

import net.minecraft.world.item.ItemStack;

public interface ContainerListener {
    /**
     * Sends the contents of an inventory slot to the client-side Container. This doesn't have to match the actual contents of that slot.
     */
    void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack stack);

    void dataChanged(AbstractContainerMenu containerMenu, int dataSlotIndex, int value);
}
