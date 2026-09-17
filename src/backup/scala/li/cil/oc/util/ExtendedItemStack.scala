package li.cil.oc.util

import li.cil.oc.common.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * 为 `ItemStack` 补回 1.7.10 时代的 NBT 访问接口。
 *
 * 1.21.1 的物品数据改由数据组件承载，OC 原有的
 * `stack.getTag() / setTagCompound() / hasTagCompound()`
 * 在这里对应到 [[li.cil.oc.common.DataComponents.NBT]]。
 */
trait ExtendedItemStack {
  implicit class ItemStackNBT(private val stack: ItemStack) {
    /** 等价于 1.7.10 的 `ItemStack#getTagCompound`。 */
    def getTag(): CompoundTag =
      if (stack == null || stack.isEmpty) null else stack.get(DataComponents.NBT.get())

    /** 等价于 1.7.10 的 `ItemStack#hasTagCompound`。 */
    def hasTag(): Boolean = getTag() != null

    /** 等价于 1.7.10 的 `ItemStack#setTagCompound`。 */
    def setTag(tag: CompoundTag): Unit =
      if (stack != null && !stack.isEmpty) stack.set(DataComponents.NBT.get(), tag)
  }
}
