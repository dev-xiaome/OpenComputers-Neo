package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「区块加载升级」（原 `li.cil.oc.common.item.UpgradeChunkloader`）。 */
class UpgradeChunkloader(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
