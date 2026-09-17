package li.cil.oc.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

import java.util.Objects
import scala.language.implicitConversions

/**
 * 物品栈的可比较包装（用于去重/排序）。
 *
 * 1.21.1 里物品不再用数字 ID 和 damage 值区分：改用注册表 ID + 数据组件比较。
 */
class ItemStackWrapper(val inner: ItemStack) extends Ordered[ItemStackWrapper] {
  private def itemId: Int =
    if (inner == null || inner.isEmpty) 0 else BuiltInRegistries.ITEM.getId(inner.getItem)

  private def componentsKey: Int =
    if (inner == null || inner.isEmpty) 0 else inner.getComponents.hashCode()

  override def compare(that: ItemStackWrapper): Int = {
    val byId = itemId - that.itemId
    if (byId != 0) byId else componentsKey - that.componentsKey
  }

  override def hashCode(): Int = Objects.hash(Int.box(itemId), Int.box(componentsKey))

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ItemStackWrapper => compare(that) == 0
    case _ => false
  }

  override def clone(): AnyRef = new ItemStackWrapper(inner)

  override def toString: String = String.valueOf(inner)
}
