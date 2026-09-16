package li.cil.oc.common.item

import java.util

import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.util.BlockPosition
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.extensions.IForgeItem
import net.minecraft.world.level.Level
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.InteractionResult
import net.minecraft.ChatFormatting
import net.minecraft.core.Direction

class Manual(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, level: Level, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, level, tooltip, flag)
    tooltip.add(Component.literal(ChatFormatting.DARK_GRAY.toString + "v" + OpenComputers.Version))
  }

  override def use(stack: ItemStack, level: Level, player: Player): InteractionResultHolder[ItemStack] = {
    if (level.isClientSide) {
      if (player.isCrouching) {
        api.Manual.reset()
      }
      api.Manual.openFor(player)
    }
    new InteractionResultHolder(InteractionResult.sidedSuccess(level.isClientSide), stack)
  }

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    val world = player.level
    api.Manual.pathFor(world, position.toBlockPos) match {
      case path: String =>
        if (world.isClientSide) {
          api.Manual.openFor(player)
          api.Manual.reset()
          api.Manual.navigate(path)
        }
        true
      case _ => super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
    }
  }
}
