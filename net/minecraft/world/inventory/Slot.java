package net.minecraft.world.inventory;

import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class Slot {
    private final int slot;
    public final Container container;
    public int index;
    public final int x;
    public final int y;

    public Slot(Container container, int slot, int x, int y) {
        this.container = container;
        this.slot = slot;
        this.x = x;
        this.y = y;
    }

    /**
     * if par2 has more items than par1, onCrafting(item,countIncrease) is called
     */
    public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
        int i = newStack.getCount() - oldStack.getCount();
        if (i > 0) {
            this.onQuickCraft(newStack, i);
        }
    }

    /**
     * Typically increases an internal count, then calls {@code onCrafting(item)}.
     *
     * @param stack the output - ie, iron ingots, and pickaxes, not ore and wood.
     */
    protected void onQuickCraft(ItemStack stack, int amount) {
    }

    protected void onSwapCraft(int numItemsCrafted) {
    }

    /**
     * @param stack the output - ie, iron ingots, and pickaxes, not ore and wood.
     */
    protected void checkTakeAchievements(ItemStack stack) {
    }

    public void onTake(Player player, ItemStack stack) {
        this.setChanged();
    }

    /**
     * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
     */
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    public ItemStack getItem() {
        return this.container.getItem(this.slot);
    }

    public boolean hasItem() {
        return !this.getItem().isEmpty();
    }

    public void setByPlayer(ItemStack stack) {
        this.setByPlayer(stack, this.getItem());
    }

    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        this.set(newStack);
    }

    /**
     * Helper method to put a stack in the slot.
     */
    public void set(ItemStack stack) {
        this.container.setItem(this.slot, stack);
        this.setChanged();
    }

    public void setChanged() {
        this.container.setChanged();
    }

    public int getMaxStackSize() {
        return this.container.getMaxStackSize();
    }

    public int getMaxStackSize(ItemStack stack) {
        return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
    }

    @Nullable
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return backgroundPair;
    }

    /**
     * Decrease the size of the stack in slot (first int arg) by the amount of the second int arg. Returns the new stack.
     */
    public ItemStack remove(int amount) {
        return this.container.removeItem(this.slot, amount);
    }

    /**
     * Return whether this slot's stack can be taken from this slot.
     */
    public boolean mayPickup(Player player) {
        return true;
    }

    public boolean isActive() {
        return true;
    }

    /**
     * Retrieves the index in the inventory for this slot, this value should typically not
     * be used, but can be useful for some occasions.
     *
     * @return Index in associated inventory for this slot.
     */
    public int getSlotIndex() {
        return slot;
    }

    /**
     * Checks if the other slot is in the same inventory, by comparing the inventory reference.
     * @param other
     * @return true if the other slot is in the same inventory
     */
    public boolean isSameInventory(Slot other) {
        return this.container == other.container;
    }

    private Pair<ResourceLocation, ResourceLocation> backgroundPair;
    /**
     * Sets the background atlas and sprite location.
     *
     * @param atlas The atlas name
     * @param sprite The sprite located on that atlas.
     * @return this, to allow chaining.
     */
    public Slot setBackground(ResourceLocation atlas, ResourceLocation sprite) {
         this.backgroundPair = Pair.of(atlas, sprite);
         return this;
    }

    public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
        if (!this.mayPickup(player)) {
            return Optional.empty();
        } else if (!this.allowModification(player) && decrement < this.getItem().getCount()) {
            return Optional.empty();
        } else {
            count = Math.min(count, decrement);
            ItemStack itemstack = this.remove(count);
            if (itemstack.isEmpty()) {
                return Optional.empty();
            } else {
                if (this.getItem().isEmpty()) {
                    this.setByPlayer(ItemStack.EMPTY, itemstack);
                }

                return Optional.of(itemstack);
            }
        }
    }

    public ItemStack safeTake(int count, int decrement, Player player) {
        Optional<ItemStack> optional = this.tryRemove(count, decrement, player);
        optional.ifPresent(p_150655_ -> this.onTake(player, p_150655_));
        return optional.orElse(ItemStack.EMPTY);
    }

    public ItemStack safeInsert(ItemStack stack) {
        return this.safeInsert(stack, stack.getCount());
    }

    public ItemStack safeInsert(ItemStack stack, int increment) {
        if (!stack.isEmpty() && this.mayPlace(stack)) {
            ItemStack itemstack = this.getItem();
            int i = Math.min(Math.min(increment, stack.getCount()), this.getMaxStackSize(stack) - itemstack.getCount());
            if (itemstack.isEmpty()) {
                this.setByPlayer(stack.split(i));
            } else if (ItemStack.isSameItemSameComponents(itemstack, stack)) {
                stack.shrink(i);
                itemstack.grow(i);
                this.setByPlayer(itemstack);
            }

            return stack;
        } else {
            return stack;
        }
    }

    public boolean allowModification(Player player) {
        return this.mayPickup(player) && this.mayPlace(this.getItem());
    }

    public int getContainerSlot() {
        return this.slot;
    }

    public boolean isHighlightable() {
        return true;
    }

    public boolean isFake() {
        return false;
    }
}
