package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「无线网卡」（原 `li.cil.oc.common.item.WirelessNetworkCard`）。 */
class WirelessNetworkCard(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)
}
