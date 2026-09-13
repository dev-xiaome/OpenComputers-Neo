package li.cil.oc.common.item

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.util.BlockPosition
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.ChatFormatting
import net.minecraft.world.level.Level

class Manual(val parent: Delegator) extends traits.Delegate {
  @SideOnly(Dist.CLIENT)
  override def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    tooltip.add(ChatFormatting.DARK_GRAY.toString + "v" + OpenComputers.Version)
    super.tooltipLines(stack, player, tooltip, advanced)
  }

  override def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (world.isRemote) {
      if (player.isSneaking) {
        api.Manual.reset()
      }
      api.Manual.openFor(player)
    }
    super.onItemRightClick(stack, world, player)
  }

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    val world = player.getEntityWorld
    api.Manual.pathFor(world, position.x, position.y, position.z) match {
      case path: String =>
        if (world.isRemote) {
          api.Manual.openFor(player)
          api.Manual.reset()
          api.Manual.navigate(path)
        }
        true
      case _ => super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
    }
  }
}
