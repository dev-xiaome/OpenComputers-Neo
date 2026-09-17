package li.cil.oc.common.item

import net.minecraft.world.item.Item

/**
 * 「网卡 LAN」（原 `li.cil.oc.common.item.NetworkCard`）。
 *
 * 注册名沿用 `Constants.ItemName.NetworkCard`（`lanCard`），不要把注册名改成类名。
 */
class NetworkCard(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
