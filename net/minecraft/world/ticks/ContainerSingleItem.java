package net.minecraft.world.ticks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ContainerSingleItem extends Container {
    ItemStack getTheItem();

    default ItemStack splitTheItem(int amount) {
        return this.getTheItem().split(amount);
    }

    void setTheItem(ItemStack item);

    default ItemStack removeTheItem() {
        return this.splitTheItem(this.getMaxStackSize());
    }

    @Override
    default int getContainerSize() {
        return 1;
    }

    @Override
    default boolean isEmpty() {
        return this.getTheItem().isEmpty();
    }

    @Override
    default void clearContent() {
        this.removeTheItem();
    }

    /**
     * Removes a stack from the given slot and returns it.
     */
    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return this.removeItem(slot, this.getMaxStackSize());
    }

    /**
     * Returns the stack in the given slot.
     */
    @Override
    default ItemStack getItem(int slot) {
        return slot == 0 ? this.getTheItem() : ItemStack.EMPTY;
    }

    /**
     * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
     */
    @Override
    default ItemStack removeItem(int slot, int amount) {
        return slot != 0 ? ItemStack.EMPTY : this.splitTheItem(amount);
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    @Override
    default void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            this.setTheItem(stack);
        }
    }

    public interface BlockContainerSingleItem extends ContainerSingleItem {
        BlockEntity getContainerBlockEntity();

        @Override
        default boolean stillValid(Player p_324363_) {
            return Container.stillValidBlockEntity(this.getContainerBlockEntity(), p_324363_);
        }
    }
}
