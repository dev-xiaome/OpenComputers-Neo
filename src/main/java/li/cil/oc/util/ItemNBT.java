package li.cil.oc.util;

import li.cil.oc.common.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 物品 NBT 访问工具（Java 侧）。
 *
 * <p>NeoForge 1.21.1 的 {@code ItemStack} 不再直接持有 {@code CompoundTag}，而是使用数据组件。
 * OC 的组件数据模型依赖**可变**的 NBT 树，因此这里把它映射到自定义组件
 * {@link DataComponents#NBT}（里面装的就是同一个 {@code CompoundTag} 实例，可就地修改）。
 *
 * <p>Scala 侧有等价的隐式扩展（{@code li.cil.oc.util.ItemStackNBTExtensions}），
 * 让 OCCE 原有的 {@code stack.getTag() / setTag(...) / hasTag()} 写法继续可用。
 * Java 不能用 Scala 隐式，所以这里必须单独提供一份静态方法。
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

    /** 取得物品上的 NBT，不存在时创建并写入一个空的。返回的实例是**活的**，可就地修改。 */
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
