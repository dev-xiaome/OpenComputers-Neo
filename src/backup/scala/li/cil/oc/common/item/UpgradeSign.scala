package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「告示牌升级」（原 `li.cil.oc.common.item.UpgradeSign`）。 */
class UpgradeSign(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
