package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import java.util

import li.cil.oc.Localization
import li.cil.oc.util.Tooltip
import li.cil.oc.util.{UpgradeExperience => ExperienceUtil}
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.{Dist, OnlyIn}

import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.network.chat.Component

class UpgradeExperience(props: Properties) extends Item(props) with traits.SimpleItem with traits.ItemTier {
  @OnlyIn(Dist.CLIENT)
  // 1.21.1：Item#appendHoverText 的第二个参数从 `Level` 换成了 `Item.TooltipContext`。
  override def appendHoverText(stack: ItemStack, context: net.minecraft.world.item.Item.TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    if (stack.hasTag) {
      val nbt = li.cil.oc.integration.opencomputers.Item.dataTag(stack)
      val experience = ExperienceUtil.getExperience(nbt)
      val level = ExperienceUtil.calculateLevelFromExperience(experience)
      val reportedLevel = ExperienceUtil.calculateExperienceLevel(level, experience)
      tooltip.add(Component.literal(Localization.Tooltip.ExperienceLevel(reportedLevel)).setStyle(Tooltip.DefaultStyle))
    }
  }
}
