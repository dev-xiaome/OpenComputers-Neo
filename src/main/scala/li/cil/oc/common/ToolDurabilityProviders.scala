package li.cil.oc.common

import java.lang.reflect.Method

import net.minecraft.world.item.ItemStack

import scala.collection.mutable

object ToolDurabilityProviders {
  private val providers = mutable.ArrayBuffer.empty[Method]

  def add(provider: Method): Unit = providers += provider

  def getDurability(stack: ItemStack): Option[Double] = {
    for (provider <- providers) {
      val durability = IMC.tryInvokeStatic(provider, stack)(Double.NaN)
      if (!durability.isNaN) return Option(durability)
    }
    // Fall back to vanilla damage values.
    // 1.21.1：`Item#canBeDepleted` 已移除，等价判断是 `ItemStack#isDamageableItem`
    // （即物品带 `MAX_DAMAGE` 组件），顺带避开了 `getMaxDamage` 为 0 时的除零。
    if (stack.isDamageableItem) Option(1.0 - stack.getDamageValue.toDouble / stack.getMaxDamage.toDouble)
    else None
  }
}
