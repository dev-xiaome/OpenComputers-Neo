package li.cil.oc.common.item.traits

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Localization
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

trait ItemTier extends Delegate {
  self: Delegate =>
  @SideOnly(Dist.CLIENT)
  override def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipLines(stack, player, tooltip, advanced)
    if (advanced) {
      tooltip.add(Localization.Tooltip.Tier(tierFromDriver(stack) + 1))
    }
  }
}
