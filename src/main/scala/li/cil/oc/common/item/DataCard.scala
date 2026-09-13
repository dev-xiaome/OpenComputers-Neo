package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「数据卡」（原 `li.cil.oc.common.item.DataCard`）。 */
class DataCard(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier
