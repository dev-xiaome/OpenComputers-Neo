package li.cil.oc.common.item.traits

import li.cil.oc.Localization
import li.cil.oc.util.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item.TooltipContext
import net.minecraft.world.item.{ItemStack, TooltipFlag}

import java.util

trait ItemTier extends SimpleItem {
  override def appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    if (flag.isAdvanced) {
      tooltip.add(Component.literal(Localization.Tooltip.Tier(tierFromDriver(stack) + 1)).setStyle(Tooltip.DefaultStyle))
    }
  }
}
