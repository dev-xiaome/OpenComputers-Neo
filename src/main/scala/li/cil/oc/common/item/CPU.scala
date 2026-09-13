package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

import scala.language.existentials

/**
 * 「处理器 CPU」（原 `li.cil.oc.common.item.CPU`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator` 构造参数。
 *  - `unlocalizedName` 由 [[li.cil.oc.common.item.traits.Delegate]] 自动拼出（类名 + tier）。
 *  - 品质在注册期由 [[CPU.tier]] 工厂固定。
 */
class CPU(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier with traits.CPULike {

  override def cpuTier: Int = tier

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)
}

object CPU {
  /** 按等级创建物品（`tier` 为 0 起的等级索引）。 */
  def tier(t: Int): CPU =
    new CPU(new Item.Properties().rarity(li.cil.oc.util.Rarity.byTier(t)), t)
}
