package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「天使升级」（原 `li.cil.oc.common.item.UpgradeAngel`）。 */
class UpgradeAngel(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
