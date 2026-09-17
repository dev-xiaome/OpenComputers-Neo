package net.minecraft.advancements.critereon;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public interface SingleComponentItemPredicate<T> extends ItemSubPredicate {
    @Override
    default boolean matches(ItemStack stack) {
        T t = stack.get(this.componentType());
        return t != null && this.matches(stack, t);
    }

    DataComponentType<T> componentType();

    boolean matches(ItemStack stack, T value);
}
