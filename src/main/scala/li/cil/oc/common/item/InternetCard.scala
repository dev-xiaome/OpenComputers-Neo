package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「互联网卡」（原 `li.cil.oc.common.item.InternetCard`）。 */
class InternetCard(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
