package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「经验升级」（原 `li.cil.oc.common.item.UpgradeExperience`）。 */
class UpgradeExperience(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
