package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「物品栏控制器升级」（原 `li.cil.oc.common.item.UpgradeInventoryController`）。 */
class UpgradeInventoryController(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
