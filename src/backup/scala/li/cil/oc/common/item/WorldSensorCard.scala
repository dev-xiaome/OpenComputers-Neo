package li.cil.oc.common.item

import net.minecraft.world.item.Item

/**
 * 「世界传感器卡」（原 `li.cil.oc.common.item.WorldSensorCard`）。
 *
 * 1.7.10 通过 `Mods.Galacticraft.isAvailable` 决定是否在创造模式标签页显示；
 * 1.21.1 的第三方模组集成整体移除（见 docs/PORTING.md「其它模组集成」），
 * 因此这里保留 [[showInItemList]] 标记，默认不在标签页显示。
 */
class WorldSensorCard(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {
  showInItemList = false
}
