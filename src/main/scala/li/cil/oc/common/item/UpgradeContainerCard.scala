package li.cil.oc.common.item

import net.minecraft.world.item.Item

/**
 * 「升级容器（卡）」（原 `li.cil.oc.common.item.UpgradeContainerCard`）。
 *
 * 对应 `Constants.ItemName.CardContainerTier1..3`。
 */
class UpgradeContainerCard(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  override protected def tooltipData: Seq[Any] = Seq(tier + 1)
}
