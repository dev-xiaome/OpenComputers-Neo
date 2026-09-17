package net.minecraft.world;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public interface WorldlyContainer extends Container {
    int[] getSlotsForFace(Direction side);

    /**
     * Returns {@code true} if automation can insert the given item in the given slot from the given side.
     */
    boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction);

    /**
     * Returns {@code true} if automation can extract the given item in the given slot from the given side.
     */
    boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction);
}
