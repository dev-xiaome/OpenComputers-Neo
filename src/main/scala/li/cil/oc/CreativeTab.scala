package li.cil.oc

import net.minecraft.world.item.CreativeModeTab

/**
 * 创造模式标签页入口。
 *
 * 1.7.10 里各物品通过 `setCreativeTab(CreativeTab)` 自行登记；
 * 1.21.1 改为注册 `CreativeModeTab` 并在 `BuildCreativeModeTabContentsEvent`
 * 中集中填充物品。实际注册在 [[li.cil.oc.api.CreativeTab]]（Java 侧）。
 */
object CreativeTab {
  def instance: CreativeModeTab = api.CreativeTab.instance()
}
