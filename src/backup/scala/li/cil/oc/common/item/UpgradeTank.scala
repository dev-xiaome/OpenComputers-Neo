package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack

/**
 * 「储罐升级」（原 `li.cil.oc.common.item.UpgradeTank`）。
 *
 * 1.21.1 迁移要点：
 *  - `FluidStack.loadFluidStackFromNBT(tag)` → `FluidStack.parseOptional(provider, tag)`
 *    （需要 `HolderLookup.Provider`；提示渲染时无法可靠取得，见下方 TODO）。
 *  - `stack.getFluid.getLocalizedName(stack)` → `stack.getHoverName`（1.21.1 的流体名就是组件名）。
 */
class UpgradeTank(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {

  override def tooltipLines(stack: ItemStack, player: net.minecraft.world.entity.player.Player,
                            tooltip: java.util.List[String], advanced: Boolean): Unit = {
    // TODO(流体): 1.21.1 的 `FluidStack.parseOptional` 需要 `HolderLookup.Provider`，
    // 而 tooltip 阶段拿不到注册表访问器；等 `common` 层接入注册表提供者后再恢复显示储量。
    super.tooltipLines(stack, player, tooltip, advanced)
  }
}
