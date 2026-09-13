package li.cil.oc.common.item

import net.minecraft.world.item.Item

/**
 * 「空白卡片基板」（原 `li.cil.oc.common.item.CardBase`）。
 *
 * 对应 `Constants.ItemName.Card`（注册名 `card`）：1.7.10 里它是 `Delegator`
 * 的一个 damage 子类型，1.21.1 改为独立物品。
 */
class CardBase(props: Item.Properties) extends Item(props) with traits.Delegate
