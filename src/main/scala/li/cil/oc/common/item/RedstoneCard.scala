package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「红石卡」（原 `li.cil.oc.common.item.RedstoneCard`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator`。
 *  - 原 `showInItemList = tier == Tier.One`（T2 是否显示取决于第三方模组集成）：
 *    1.21.1 里展示与否由 [[li.cil.oc.common.init.Registry]] 的注册顺序决定，
 *    这里保留 [[showInItemList]] 标记，注册层用它决定是否加入创造模式标签页。
 *  - 对第三方红石模组的提示（ProjectRed / RedLogic / MFR / WirelessRedstone）已移除，
 *    因为这些集成不在本次移植范围（见 docs/PORTING.md「其它模组集成」）。
 */
class RedstoneCard(props: Item.Properties, val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  // Note: T2 is enabled in mod integration, if it makes sense.
  showInItemList = tier == li.cil.oc.common.Tier.One

  override protected def tooltipExtended(stack: ItemStack, tooltip: java.util.List[String]): Unit = {
    // TODO(集成): 第三方红石模组（ProjectRed / RedLogic / MFR / WirelessRedstone）
    // 的额外提示行，等集成层移植后按 `Mods.*.isAvailable` 补回。
    super.tooltipExtended(stack, tooltip)
  }
}
