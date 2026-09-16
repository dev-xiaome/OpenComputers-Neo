package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ItemUtils
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

class HoverBootsData extends ItemData(Constants.ItemName.HoverBoots) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  var charge = 0.0

  private final val ChargeTag = Settings.namespace + "charge"

  override def loadData(holder: DataComponentHolder): Unit = {
    charge = holder.getComponent(OCComponents.CHARGE) getOrElse 0.0
  }
  
  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.CHARGE, charge)
  }
}
