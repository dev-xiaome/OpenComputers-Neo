package li.cil.oc.common.item

import java.util

import li.cil.oc.Settings
import li.cil.oc.util.Tooltip
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class LinkedCard(val parent: Delegator) extends traits.Delegate with traits.ItemTier {
  override def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    if (stack.hasTagCompound && stack.getTagCompound.contains(Settings.namespace + "data")) {
      val data = stack.getTagCompound.getCompound(Settings.namespace + "data")
      if (data.contains(Settings.namespace + "tunnel")) {
        val channel = data.getString(Settings.namespace + "tunnel")
        if (channel.length > 13) {
          tooltip.addAll(Tooltip.get(unlocalizedName + "_Channel", channel.substring(0, 13) + "..."))
        }
        else {
          tooltip.addAll(Tooltip.get(unlocalizedName + "_Channel", channel))
        }
      }
    }
    super.tooltipLines(stack, player, tooltip, advanced)
  }
}
