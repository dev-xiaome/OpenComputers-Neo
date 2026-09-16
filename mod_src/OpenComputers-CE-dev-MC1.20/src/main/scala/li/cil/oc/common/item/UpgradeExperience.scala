package li.cil.oc.common.item

import java.util

import li.cil.oc.Localization
import li.cil.oc.util.Tooltip
import li.cil.oc.util.{UpgradeExperience => ExperienceUtil}
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.{Dist, OnlyIn}
import net.minecraftforge.common.extensions.IForgeItem
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.network.chat.Component

class UpgradeExperience(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier {
  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, level: Level, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, level, tooltip, flag)
    if (stack.hasTag) {
      val nbt = li.cil.oc.integration.opencomputers.Item.dataTag(stack)
      val experience = ExperienceUtil.getExperience(nbt)
      val level = ExperienceUtil.calculateLevelFromExperience(experience)
      val reportedLevel = ExperienceUtil.calculateExperienceLevel(level, experience)
      tooltip.add(Component.literal(Localization.Tooltip.ExperienceLevel(reportedLevel)).setStyle(Tooltip.DefaultStyle))
    }
  }
}
