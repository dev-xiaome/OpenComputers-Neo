package net.minecraft.world.level.storage.loot;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public interface ContainerComponentManipulator<T> {
    DataComponentType<T> type();

    T empty();

    T setContents(T contents, Stream<ItemStack> items);

    Stream<ItemStack> getContents(T contents);

    default void setContents(ItemStack stack, T contents, Stream<ItemStack> items) {
        T t = stack.getOrDefault(this.type(), contents);
        T t1 = this.setContents(t, items);
        stack.set(this.type(), t1);
    }

    default void setContents(ItemStack stack, Stream<ItemStack> items) {
        this.setContents(stack, this.empty(), items);
    }

    default void modifyItems(ItemStack stack, UnaryOperator<ItemStack> modifier) {
        T t = stack.get(this.type());
        if (t != null) {
            UnaryOperator<ItemStack> unaryoperator = p_344668_ -> {
                if (p_344668_.isEmpty()) {
                    return p_344668_;
                } else {
                    ItemStack itemstack = modifier.apply(p_344668_);
                    itemstack.limitSize(itemstack.getMaxStackSize());
                    return itemstack;
                }
            };
            this.setContents(stack, this.getContents(t).map(unaryoperator));
        }
    }
}
