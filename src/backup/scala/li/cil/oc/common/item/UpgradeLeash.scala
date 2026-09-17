package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「牵引绳升级」（原 `li.cil.oc.common.item.UpgradeLeash`）。 */
class UpgradeLeash(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
