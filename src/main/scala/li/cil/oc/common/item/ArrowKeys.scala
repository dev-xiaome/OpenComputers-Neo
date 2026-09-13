package li.cil.oc.common.item

import net.minecraft.world.item.Item

/**
 * 「箭头键」（原 `li.cil.oc.common.item.ArrowKeys`）。
 *
 * 1.7.10 通过 `Delegator` 的 damage 子类型实现，1.21.1 每个子类型独立注册一个物品，
 * 因此构造参数 `parent: Delegator` 被删除，改由 `Item.Properties` 提供属性。
 */
class ArrowKeys(props: Item.Properties) extends Item(props) with traits.Delegate {
  override protected def tooltipName: Option[String] = None
}
