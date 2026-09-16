package li.cil.oc.common.item

import java.util

import li.cil.oc.Settings
import li.cil.oc.util.Tooltip
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.extensions.IForgeItem
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class UpgradeTank(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier {
  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, level: Level, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, level, tooltip, flag)
    if (stack.hasTag) {
      FluidStack.loadFluidStackFromNBT(stack.getTag.getCompound(Settings.namespace + "data")) match {
        case stack: FluidStack =>
          tooltip.add(Component.literal(stack.getDisplayName.getString + ": " + stack.getAmount + "/16000").setStyle(Tooltip.DefaultStyle))
        case _ =>
      }
    }
  }
}
