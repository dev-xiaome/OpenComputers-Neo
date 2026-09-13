package li.cil.oc.common.item

import li.cil.oc.util.Rarity
import net.minecraft.world.item.Item

/**
 * 「显卡」（原 `li.cil.oc.common.item.GraphicsCard`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator` 构造参数。
 *  - `unlocalizedName` 由 [[li.cil.oc.common.item.traits.Delegate]] 自动拼出。
 *  - 品质在注册期由 [[GraphicsCard.tier]] 工厂固定。
 */
class GraphicsCard(props: Item.Properties, val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier with traits.GPULike {

  override def gpuTier: Int = tier

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)
}

object GraphicsCard {
  /** 按等级创建物品（`tier` 为 0 起的等级索引）。 */
  def tier(t: Int): GraphicsCard =
    new GraphicsCard(new Item.Properties().rarity(Rarity.byTier(t)), t)
}
