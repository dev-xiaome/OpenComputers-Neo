package li.cil.oc.common.item.data

import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.api
import li.cil.oc.api.Persistable
import li.cil.oc.util.Rarity
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

abstract class ItemData(val itemName: String) extends Persistable {
  def loadData(stack: ItemStack): Unit = {
    if (stack.hasTag) {
      // Because ItemStack's load function doesn't copy the compound tag,
      // but keeps it as is, leading to oh so fun bugs!
      loadData(stack.getTag.copy().asInstanceOf[CompoundTag])
    }
  }

  def saveData(stack: ItemStack): Unit = {
    saveData(stack.getOrCreateTag)
    applyRarityComponent(stack)
  }

  /**
   * 把 `tier` 对应的品质写进堆叠的 `RARITY` 数据组件。
   *
   * 1.21.1 的迁移背景：物品品质不再是「物品按堆叠现算」——`Item#getRarity(ItemStack)` 已被原版移除，
   * `ItemStack#getRarity()` 只读 `DataComponents.RARITY`（缺省时回落到 `Item.Properties#rarity`
   * 写进去的静态值，再缺省就是 `COMMON`）。因此「tier 存在堆叠数据里、品质随 tier 变」的物品
   * （单片机 / 机器人 / 无人机 / 平板）必须在写数据时顺手把组件写上，否则会退化成 COMMON。
   *
   * 默认什么都不做（品质沿用注册时的静态值）；有 tier 的子类覆写本方法。
   * 组件与 NBT 里的 tier 同源，因此幂等，不会影响存档兼容性。
   */
  protected def applyRarityComponent(stack: ItemStack): Unit = ()

  /** 子类共用的写组件写法；`tier` 的取值映射见 [[li.cil.oc.util.Rarity.byTier]]。 */
  protected final def setRarityFromTier(stack: ItemStack, tier: Int): Unit =
    stack.set(DataComponents.RARITY, Rarity.byTier(tier))

  def createItemStack() = {
    if (itemName == null) ItemStack.EMPTY
    else {
      val stack = api.Items.get(itemName).createItemStack(1)
      saveData(stack)
      stack
    }
  }
}
