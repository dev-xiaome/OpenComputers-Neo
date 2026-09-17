package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IForgeItem

class UpgradeTrading(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier {
  override protected def tooltipName: Option[String] = Option(unlocalizedName)
}
