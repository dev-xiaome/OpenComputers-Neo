package li.cil.oc.common

import net.minecraft.world.item.ItemStack

import scala.collection.mutable

/**
 * 工具耐久度提供者注册表。
 *
 * 第三方模组可以通过 IMC 注册一个 `(ItemStack) => double` 形态的静态方法，
 * 返回 0..1 的剩余耐久比例；没有提供者时回退到原版耐久值。
 */
object ToolDurabilityProviders {
  private val providers = mutable.ArrayBuffer.empty[java.lang.reflect.Method]

  def add(provider: java.lang.reflect.Method): Unit = providers += provider

  def getDurability(stack: ItemStack): Option[Double] = {
    for (provider <- providers) {
      val durability = Reflection.tryInvokeStatic(provider, stack)(Double.NaN)
      if (!durability.isNaN) return Option(durability)
    }
    // Fall back to vanilla damage values.
    if (stack != null && stack.isDamageableItem) {
      val max = stack.getMaxDamage
      if (max <= 0) Option(1.0)
      else Option(1.0 - stack.getDamageValue.toDouble / max.toDouble)
    }
    else None
  }
}
