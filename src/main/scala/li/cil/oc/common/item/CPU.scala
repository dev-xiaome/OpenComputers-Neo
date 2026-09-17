package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties


import scala.language.existentials

class CPU(props: Properties, val tier: Int) extends Item(props) with traits.SimpleItem with traits.ItemTier with traits.CPULike {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

  override def cpuTier = tier

  override protected def tooltipName = Option(unlocalizedName)
}
