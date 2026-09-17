package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import java.util

import li.cil.oc.Settings
import li.cil.oc.util.Tooltip
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class UpgradeTank(props: Properties) extends Item(props) with traits.SimpleItem with traits.ItemTier {
  @OnlyIn(Dist.CLIENT)
  // 1.21.1：Item#appendHoverText 的第二个参数从 `Level` 换成了 `Item.TooltipContext`。
  override def appendHoverText(stack: ItemStack, context: net.minecraft.world.item.Item.TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    if (stack.hasTag) {
      // 1.21.1：`FluidStack.loadFluidStackFromNBT` 已由 `parseOptional(provider, tag)` 取代
      // （需要注册表上下文；解析失败时返回 `FluidStack.EMPTY`）。
      val fluid = FluidStack.parseOptional(li.cil.oc.util.RegistryAccessHelper.getOrEmpty(), stack.getTag.getCompound(Settings.namespace + "data"))
      if (!fluid.isEmpty) {
        tooltip.add(Component.literal(fluid.getDisplayName.getString + ": " + fluid.getAmount + "/16000").setStyle(Tooltip.DefaultStyle))
      }
    }
  }
}
