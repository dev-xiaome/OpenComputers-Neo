package li.cil.oc.common.item

import net.minecraft.world.item.Item

/**
 * 「抽象总线卡」（原 `li.cil.oc.common.item.AbstractBusCard`）。
 *
 * 1.7.10 通过 `Mods.StargateTech2.isAvailable` 决定是否在创造模式标签页显示；
 * 1.21.1 的第三方模组集成整体移除（见 docs/PORTING.md「其它模组集成」），
 * 因此这里保留 [[showInItemList]] 标记，默认不在标签页显示，
 * 由 [[li.cil.oc.common.init.Registry]] 依据该标记决定是否加入。
 */
class AbstractBusCard(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {
  showInItemList = false
}
