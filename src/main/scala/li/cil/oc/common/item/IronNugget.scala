package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「铁粒」（原 `li.cil.oc.common.item.IronNugget`），对应 `Constants.ItemName.IronNugget`（注册名 `nuggetIron`）。 */
class IronNugget(props: Item.Properties) extends Item(props) with traits.Delegate
