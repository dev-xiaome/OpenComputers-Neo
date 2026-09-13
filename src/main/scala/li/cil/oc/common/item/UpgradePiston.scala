package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「活塞升级」（原 `li.cil.oc.common.item.UpgradePiston`）。 */
class UpgradePiston(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
