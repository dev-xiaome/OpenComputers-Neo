package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「组件总线」（原 `li.cil.oc.common.item.ComponentBus`）。
 *
 * 1.21.1 迁移要点：品质在注册期由 [[ComponentBus.tier]] 工厂固定；
 * 创造版（`tier == Tier.Four`）因为驱动把它当作 T3，需要单独指定品质。
 */
class ComponentBus(props: Item.Properties, val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  override protected def tooltipData: Seq[Any] = Seq(Settings.get.cpuComponentSupport(tier))
}

object ComponentBus {
  /** 按等级创建物品（`tier` 为 0 起的等级索引；`Tier.Four` 表示创造版）。 */
  def tier(t: Int): ComponentBus = {
    // Because the driver considers the creative bus to be tier 3, the superclass
    // will believe it has T3 rarity. We override that here.
    val rarity = if (t == li.cil.oc.common.Tier.Four) li.cil.oc.util.Rarity.byTier(li.cil.oc.common.Tier.Four)
    else li.cil.oc.util.Rarity.byTier(t)
    new ComponentBus(new Item.Properties().rarity(rarity).stacksTo(1), t)
  }
}
