package li.cil.oc.common.item

import li.cil.oc.Localization
import li.cil.oc.util.{Tooltip, UpgradeExperience => ExperienceUtil}
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item.{Properties, TooltipContext}
import net.minecraft.world.item.{Item, ItemStack, TooltipFlag}
import net.neoforged.neoforge.common.extensions.IItemExtension

import java.util

class UpgradeExperience(props: Properties) extends Item(props) with traits.SimpleItem with traits.ItemTier with IItemExtension {
  override def appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    if (stack.has(DataComponents.CUSTOM_DATA)) {
      val nbt = li.cil.oc.integration.opencomputers.Item.dataTag(stack)
      val experience = ExperienceUtil.getExperience(nbt)
      val level = ExperienceUtil.calculateLevelFromExperience(experience)
      val reportedLevel = ExperienceUtil.calculateExperienceLevel(level, experience)
      tooltip.add(Component.literal(Localization.Tooltip.ExperienceLevel(reportedLevel)).setStyle(Tooltip.DefaultStyle))
    }
  }
}
