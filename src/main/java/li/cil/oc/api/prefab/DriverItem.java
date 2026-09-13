package li.cil.oc.api.prefab;

import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.util.ItemNBT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * If you wish to create item components such as the network card or hard drives
 * you will need an item driver.
 * <br>
 * This prefab allows creating a driver that works for a specified list of item
 * stacks (to support different items with the same id but different damage
 * values). It also takes care of creating and getting the tag compound on an
 * item stack to save data to or load data from.
 * <br>
 * You still have to specify your component's slot type and provide the
 * implementation for creating its environment, if any.
 * <br>
 * <b>1.21.1 移植说明</b>：
 * <ul>
 * <li>{@link ItemStack} 不再持有 {@code CompoundTag}（没有
 * {@code getTagCompound()/setTagCompound()/hasTagCompound()}），物品数据改由数据组件承载。
 * 本类因此改用 {@link ItemNBT}（{@code get/getOrCreate/has/set}）访问挂在物品上的 NBT。</li>
 * <li>{@code isItemEqual} 被移除（没有 damage 概念），这里改用
 * {@link ItemStack#isSameItem(ItemStack, ItemStack)}，即只比较物品类型、忽略数据组件。</li>
 * </ul>
 *
 * @see li.cil.oc.api.network.ManagedEnvironment
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class DriverItem implements li.cil.oc.api.driver.Item {
    protected final ItemStack[] items;

    protected DriverItem(final ItemStack... items) {
        this.items = items.clone();
    }

    @Override
    public boolean worksWith(final ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            for (ItemStack item : items) {
                if (item != null && !item.isEmpty() && ItemStack.isSameItem(item, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int tier(final ItemStack stack) {
        return 0;
    }

    @Override
    public CompoundTag dataTag(final ItemStack stack) {
        // 1.21.1：物品 NBT 通过自定义数据组件保存，ItemNBT.getOrCreate 等价于
        // 1.7.10 的 “没有就 new 一个并 setTagCompound”。
        final CompoundTag nbt = ItemNBT.getOrCreate(stack);
        // This is the suggested key under which to store item component data.
        // You are free to change this as you please.
        if (!nbt.contains("oc:data")) {
            nbt.put("oc:data", new CompoundTag());
        }
        return nbt.getCompound("oc:data");
    }

    // Convenience methods provided for HostAware drivers.

    protected boolean isAdapter(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Adapter.class.isAssignableFrom(host);
    }

    protected boolean isComputer(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Case.class.isAssignableFrom(host);
    }

    protected boolean isRobot(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Robot.class.isAssignableFrom(host);
    }

    protected boolean isRotatable(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Rotatable.class.isAssignableFrom(host);
    }

    protected boolean isServer(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Server.class.isAssignableFrom(host);
    }

    protected boolean isTablet(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);
    }
}
