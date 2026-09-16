package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.ImmutableItemStack
import li.cil.oc.common.Tier
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.util.ItemUtils
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.server.ServerLifecycleHooks

class MicrocontrollerData(itemName: String = Constants.BlockName.Microcontroller) extends ItemData(itemName) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  var tier = Tier.One
  var components: Array[ItemStack] = Array[ItemStack](ItemStack.EMPTY)
  var storedEnergy = 0

  override def loadData(holder: DataComponentHolder): Unit = {
    tier = holder.getComponent(OCComponents.TIER).getOrElse(default = 0.asInstanceOf[Byte]).toInt
    components = holder.getComponent(OCComponents.COMPONENTS).getOrElse(List.empty).filter(!_.isEmpty).map(_.mutableCopy()).toArray
    storedEnergy = holder.getComponent(OCComponents.STORED_ENERGY) getOrElse 0

    // Reserve slot for EEPROM if necessary, avoids having to resize the
    // components array in the MCU tile entity, which isn't possible currently.
    if (!components.exists(stack => api.Items.get(stack) == api.Items.get(Constants.ItemName.EEPROM))) {
      components :+= ItemStack.EMPTY
    }
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.TIER, tier.toByte)
    holder.setComponent(OCComponents.COMPONENTS, components.map(ImmutableItemStack.copyOf).toList)
    holder.setComponent(OCComponents.STORED_ENERGY, storedEnergy)
  }

  def copyItemStack(): ItemStack = {
    val stack = createItemStack()
    val newInfo = new MicrocontrollerData(stack)
    newInfo.saveData(stack)
    stack
  }
}
