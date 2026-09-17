package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「储罐控制器升级」（原 `li.cil.oc.common.item.UpgradeTankController`）。 */
class UpgradeTankController(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
