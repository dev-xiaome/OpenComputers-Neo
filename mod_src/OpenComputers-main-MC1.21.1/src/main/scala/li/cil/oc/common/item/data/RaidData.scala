package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.api.ImmutableItemStack
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.MutableDataComponentHolder

class RaidData extends ItemData(Constants.BlockName.Raid) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  var disks = Array.empty[ItemStack]
  var label: Option[String] = None

  override def loadData(holder: DataComponentHolder): Unit = {
    disks = holder.getComponent(OCComponents.COMPONENTS).map(a => a.toArray.map(_.mutableCopy())).getOrElse(Array.empty)
    label = holder.getComponent(OCComponents.LABEL)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.CONTENTS, disks.map(ImmutableItemStack.copyOf).toList)
    holder.setComponent(OCComponents.LABEL, label)
  }
}
