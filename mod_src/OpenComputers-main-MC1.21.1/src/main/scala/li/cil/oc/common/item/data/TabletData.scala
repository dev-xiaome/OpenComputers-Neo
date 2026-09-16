package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.api.ImmutableItemStack
import li.cil.oc.common.Tier
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.MutableDataComponentHolder

class TabletData extends ItemData(Constants.ItemName.Tablet) {
  def this(stack: ItemStack) = {
    this()
    loadData(stack)
  }

  var items = Array.fill[ItemStack](32)(ItemStack.EMPTY)
  var isRunning = false
  var energy = 0.0
  var maxEnergy = 0.0
  var tier = Tier.One
  var container = ItemStack.EMPTY

  override def loadData(holder: DataComponentHolder): Unit = {
    for(contents <- holder.getComponent(OCComponents.CONTENTS)) {
      for(itemStack -> i <- contents.take(items.length).zipWithIndex) {
        items(i) = itemStack.mutableCopy()
      }
    }
    isRunning = holder.getComponent(OCComponents.IS_RUNNING) getOrElse false
    energy = holder.getComponent(OCComponents.CHARGE) getOrElse 0
    maxEnergy = holder.getComponent(OCComponents.MAX_CHARGE) getOrElse 0
    tier = holder.getComponent(OCComponents.TIER).map(_.toInt) getOrElse 0
    container = (holder.getComponent(OCComponents.ATTACHMENT) getOrElse ImmutableItemStack.EMPTY).mutableCopy()
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.CONTENTS, items.map(ImmutableItemStack.copyOf).toList)
    holder.setComponent(OCComponents.IS_RUNNING, isRunning)
    holder.setComponent(OCComponents.CHARGE, energy)
    holder.setComponent(OCComponents.MAX_CHARGE, maxEnergy)
    holder.setComponent(OCComponents.TIER, tier.toByte)
    holder.setComponent(OCComponents.ATTACHMENT, Option.when(!container.isEmpty) { ImmutableItemStack.copyOf(container) })
  }
}
