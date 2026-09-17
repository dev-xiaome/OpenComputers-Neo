package li.cil.oc.util;

import li.cil.oc.common.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 物品 NBT 访问工具。
 * <p>
 * 1.21.1 的 {@code ItemStack} 不再直接持有 {@code CompoundTag}，而是使用数据组件。
 * OpenComputers 的组件数据模型（{@code li.cil.oc.api.driver.Item#dataTag}）依赖可变
 * 的 NBT 树，因此这里把它映射到自定义组件 {@link DataComponents#NBT} 上。
 */
public final class ItemNBT {
    private ItemNBT() {
    }

    /** 取得物品上的 NBT，不存在时返回 {@code null}。 */
    public static CompoundTag get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.get(DataComponents.NBT.get());
    }

    /** 取得物品上的 NBT，不存在时创建并写入一个空的。 */
    public static CompoundTag getOrCreate(ItemStack stack) {
        CompoundTag tag = get(stack);
        if (tag == null) {
            tag = new CompoundTag();
            stack.set(DataComponents.NBT.get(), tag);
        }
        return tag;
    }

    public static boolean has(ItemStack stack) {
        return get(stack) != null;
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(DataComponents.NBT.get(), tag);
    }
}
