package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * Hover Boots 的能量数据（原 1.7.10 的 `HoverBootsData`）。
 *
 * 1.21.1 迁移要点：字段读写语义不变，仅 `save` 需要先保证数据组件存在
 * （由 [[ItemData.save]] 负责）。
 */
class HoverBootsData extends ItemData(Constants.ItemName.HoverBoots) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var charge: Double = 0.0

  override def load(nbt: CompoundTag): Unit = {
    charge = nbt.getDouble(Settings.namespace + "charge")
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.putDouble(Settings.namespace + "charge", charge)
  }
}
