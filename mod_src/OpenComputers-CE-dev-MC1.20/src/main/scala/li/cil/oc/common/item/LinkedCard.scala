package li.cil.oc.common.item

import java.util

import li.cil.oc.Settings
import li.cil.oc.util.Tooltip
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.extensions.IForgeItem

import scala.collection.convert.ImplicitConversionsToScala._
import net.minecraft.world.level.Level
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

class LinkedCard(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier {
  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, level: Level, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, level, tooltip, flag)
    if (stack.hasTag && stack.getTag.contains(Settings.namespace + "data")) {
      val data = stack.getTag.getCompound(Settings.namespace + "data")
      if (data.contains(Settings.namespace + "tunnel")) {
        val channel = data.getString(Settings.namespace + "tunnel")
        if (channel.length > 13) {
          for (curr <- Tooltip.get(unlocalizedName + "_channel", channel.substring(0, 13) + "...")) {
            tooltip.add(Component.literal(curr).setStyle(Tooltip.DefaultStyle))
          }
        }
        else {
          for (curr <- Tooltip.get(unlocalizedName + "_channel", channel)) {
            tooltip.add(Component.literal(curr).setStyle(Tooltip.DefaultStyle))
          }
        }
      }
    }
  }
}
