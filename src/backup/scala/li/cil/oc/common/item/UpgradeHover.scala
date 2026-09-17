package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item

/** 「悬浮升级」（原 `li.cil.oc.common.item.UpgradeHover`）。 */
class UpgradeHover(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  override protected def tooltipData: Seq[Any] = Seq(Settings.get.upgradeFlightHeight(tier))
}
