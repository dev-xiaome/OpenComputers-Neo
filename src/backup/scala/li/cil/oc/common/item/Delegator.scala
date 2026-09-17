package li.cil.oc.common.item

import li.cil.oc.common.init.Registry
import net.minecraft.world.item.ItemStack

/**
 * 1.7.10 `Delegator` 的**兼容残余**（不再是 `Item`）。
 *
 * 1.7.10 里 `Delegator` 是「一个物品 + damage 子类型」的派发容器，`Delegator.subItem(stack)`
 * 返回该堆叠对应的子类型 [[li.cil.oc.common.item.traits.Delegate]]。
 *
 * 1.21.1 改为**每个 `Constants.ItemName.*` 一个独立 `Item`**，因此：
 *  - 这里不再有 `class Delegator extends Item`，也没有 `subItems` 数组与 damage 派发；
 *  - 保留的 `object Delegator` 只是给既有调用点（未移植的 `tileentity` / `integration` /
 *    `server` 等包里的 `Delegator.subItem(stack)`）提供等价的**名称 → 行为对象**查询：
 *    物品本身若是 [[li.cil.oc.common.item.traits.Delegate]]（所有 OC 物品都是），
 *    直接返回它自己。
 *
 * 因此 `Delegator.subItem(stack)` 的语义等价于旧版：拿到「该堆叠的行为实现」。
 * 等所有调用点都改成直接用 `Registry.get(stack)` 后，本文件即可删除。
 *
 * @note 依赖 `damage` 值的旧签名（`subItem(damage: Int)`）在 1.21.1 没有对应物，
 *       恒返回 `None`。
 */
object Delegator {

  /** 该堆叠对应的行为对象；不是 OC 物品时返回 `None`。 */
  def subItem(stack: ItemStack): Option[traits.Delegate] = {
    if (stack == null || stack.isEmpty) return None
    Registry.get(stack) match {
      case null => None
      case _ => stack.getItem match {
        case delegate: traits.Delegate => Some(delegate)
        case _ => None
      }
    }
  }

  /** 1.7.10 旧签名的占位实现：1.21.1 不再用 damage 值区分子类型。 */
  @deprecated("1.21.1 不再使用 damage 值区分子类型，请改用 Registry.get(stack)", "1.0.0")
  def subItem(damage: Int): Option[traits.Delegate] = None

  /** 原 `Delegator#internalGetItemStackDisplayName` 的等价物。 */
  def internalGetItemStackDisplayName(stack: ItemStack): String =
    if (stack == null || stack.isEmpty) "" else stack.getHoverName.getString
}
