package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「物品栏升级」（原 `li.cil.oc.common.item.UpgradeInventory`）。 */
class UpgradeInventory(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
