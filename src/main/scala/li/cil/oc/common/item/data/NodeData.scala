package li.cil.oc.common.item.data

import li.cil.oc.api.network.Visibility
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.MutableDataComponentHolder

// Generic one for items that are used as components; gets the items node info.
class NodeData extends ItemData(null) {
  def this(stack: ItemStack) = {
    this()
    loadData(stack)
  }

  var address: Option[String] = None
  var buffer: Option[Double] = None
  var visibility: Option[Visibility] = None

  override def loadData(holder: DataComponentHolder): Unit = {
    for(addr <- holder.getComponent(OCComponents.ADDRESS)) {
      address = Some(addr)
    }

    for(vis <- holder.getComponent(OCComponents.VISIBILITY)) {
      visibility = Some(vis)
    }

    for(charge <- holder.getComponent(OCComponents.CHARGE)) {
      buffer = Some(charge)
    }
  }
  
  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.ADDRESS, address)
    holder.setComponent(OCComponents.VISIBILITY, visibility)
    holder.setComponent(OCComponents.CHARGE, buffer)
  }
}
