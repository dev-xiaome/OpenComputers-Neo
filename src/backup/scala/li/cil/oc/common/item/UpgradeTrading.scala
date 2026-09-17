package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「交易升级」（原 `li.cil.oc.common.item.UpgradeTrading`）。 */
class UpgradeTrading(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {
  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)
}
