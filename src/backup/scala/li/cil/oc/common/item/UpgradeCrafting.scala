package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「合成升级」（原 `li.cil.oc.common.item.UpgradeCrafting`）。 */
class UpgradeCrafting(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
