package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

class HoverBootsData extends ItemData(Constants.ItemName.HoverBoots) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var charge = 0.0

  override def load(nbt: CompoundTag): Unit = {
    charge = nbt.getDouble(Settings.namespace + "charge")
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.putDouble(Settings.namespace + "charge", charge)
  }
}
