package li.cil.oc.common.item

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack

class UpgradeTank(val parent: Delegator) extends traits.Delegate with traits.ItemTier {
  @SideOnly(Dist.CLIENT) override
  def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean) = {
    if (stack.hasTagCompound) {
      FluidStack.loadFluidStackFromNBT(stack.getTagCompound.getCompound(Settings.namespace + "data")) match {
        case stack: FluidStack =>
          tooltip.add(stack.getFluid.getLocalizedName(stack) + ": " + stack.amount + "/16000")
        case _ =>
      }
    }
    super.tooltipLines(stack, player, tooltip, advanced)
  }
}
