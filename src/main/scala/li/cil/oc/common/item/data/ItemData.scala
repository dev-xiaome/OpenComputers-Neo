package li.cil.oc.common.item.data

import li.cil.oc.api
import li.cil.oc.api.Persistable
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

abstract class ItemData(val itemName: String) extends Persistable {
  def load(stack: ItemStack): Unit = {
    if (stack.hasTagCompound) {
      // Because ItemStack's load function doesn't copy the compound tag,
      // but keeps it as is, leading to oh so fun bugs!
      load(stack.getTagCompound.copy().asInstanceOf[CompoundTag])
    }
  }

  def save(stack: ItemStack): Unit = {
    if (!stack.hasTagCompound) {
      stack.put(new CompoundTag())
    }
    save(stack.getTagCompound)
  }

  def createItemStack() = {
    if (itemName == null) null
    else {
      val stack = api.Items.get(itemName).createItemStack(1)
      save(stack)
      stack
    }
  }
}
