package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties


class GraphicsCard(props: Properties, val tier: Int) extends Item(props) with traits.SimpleItem with traits.ItemTier with traits.GPULike {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

  override def gpuTier = tier

  override protected def tooltipName = Option(unlocalizedName)
}