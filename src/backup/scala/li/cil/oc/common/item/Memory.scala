package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity

import scala.language.existentials

/**
 * 「内存条」（原 `li.cil.oc.common.item.Memory`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除了 `parent: Delegator` 构造参数（damage 派发机制已废弃）。
 *  - `unlocalizedName` 由 [[li.cil.oc.common.item.traits.Delegate]] 依据 `tier` 自动拼出
 *    （类名 + tier），因此子类不再覆写，避免重复拼接。
 *  - 品质（rarity）在 1.21.1 是注册期属性，由伴生对象的 [[Memory.tier]] 工厂
 *    通过 `Item.Properties#rarity` 固定。
 */
class Memory(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)
}

object Memory {
  /** 按等级创建物品（`tier` 为 0 起的等级索引），品质随等级提升。 */
  def tier(t: Int): Memory =
    new Memory(new Item.Properties().rarity(li.cil.oc.util.Rarity.byTier(t)), t)
}
