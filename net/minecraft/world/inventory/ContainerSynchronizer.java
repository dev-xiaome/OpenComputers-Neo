package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public interface ContainerSynchronizer {
    void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carriedItem, int[] initialData);

    void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack itemStack);

    void sendCarriedChange(AbstractContainerMenu containerMenu, ItemStack stack);

    void sendDataChange(AbstractContainerMenu container, int id, int value);
}
