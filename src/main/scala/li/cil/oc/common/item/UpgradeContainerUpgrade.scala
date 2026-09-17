package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties


class UpgradeContainerUpgrade(props: Properties, val tier: Int) extends Item(props) with traits.SimpleItem with traits.ItemTier {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

  override protected def tooltipName = Option(unlocalizedName)

  override protected def tooltipData = Seq(tier + 1)
}
