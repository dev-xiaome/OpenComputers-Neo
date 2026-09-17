package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties


class UpgradeStickyPiston(props: Properties) extends Item(props) with traits.SimpleItem with traits.ItemTier {
  override protected def tooltipName: Option[String] = Option(unlocalizedName)
}

