package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.Localization
import li.cil.oc.util.Tooltip
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraft.world.level.Level
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

trait ItemTier extends SimpleItem {
  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, level: Level, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, level, tooltip, flag)
    if (flag.isAdvanced) {
      tooltip.add(Component.literal(Localization.Tooltip.Tier(tierFromDriver(stack) + 1)).setStyle(Tooltip.DefaultStyle))
    }
  }
}
