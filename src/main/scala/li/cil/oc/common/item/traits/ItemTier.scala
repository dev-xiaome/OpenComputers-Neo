package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.Localization
import li.cil.oc.util.Tooltip
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.minecraft.world.level.Level
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

trait ItemTier extends SimpleItem {
  @OnlyIn(Dist.CLIENT)
  // 1.21.1：Item#appendHoverText 的第二个参数从 `Level` 换成了 `Item.TooltipContext`。
  override def appendHoverText(stack: ItemStack, context: net.minecraft.world.item.Item.TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    if (flag.isAdvanced) {
      tooltip.add(Component.literal(Localization.Tooltip.Tier(tierFromDriver(stack) + 1)).setStyle(Tooltip.DefaultStyle))
    }
  }
}
