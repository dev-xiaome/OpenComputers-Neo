package li.cil.oc.common.item

import li.cil.oc.{api, OpenComputers}
import net.minecraft.network.chat.Component
import net.minecraft.world.{InteractionHand, InteractionResult, InteractionResultHolder}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.{Item, ItemStack, TooltipFlag}
import net.minecraft.world.item.Item.{Properties, TooltipContext}
import net.minecraft.world.level.Level
import net.minecraft.ChatFormatting
import net.minecraft.world.item.context.UseOnContext
import net.neoforged.neoforge.common.extensions.IItemExtension

import java.util

class Manual(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    tooltip.add(Component.literal(ChatFormatting.DARK_GRAY.toString + "v" + OpenComputers.Version))
  }

  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    if (level.isClientSide) {
      if (player.isCrouching) {
        api.Manual.reset()
      }
      api.Manual.openFor(player)
    }
    InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
  }

  override def useOn(ctx: UseOnContext): InteractionResult = {
    val level = ctx.getLevel
    api.Manual.pathFor(level, ctx.getClickedPos) match {
      case path: String =>
        if (level.isClientSide && ctx.getPlayer != null) {
          api.Manual.openFor(ctx.getPlayer)
          api.Manual.reset()
          api.Manual.navigate(path)
        }
        InteractionResult.sidedSuccess(level.isClientSide)
      case _ => super.useOn(ctx)
    }
  }
}
