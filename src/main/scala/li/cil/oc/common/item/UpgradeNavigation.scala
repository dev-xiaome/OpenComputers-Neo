package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「导航升级」（原 `li.cil.oc.common.item.UpgradeNavigation`）。 */
class UpgradeNavigation(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
