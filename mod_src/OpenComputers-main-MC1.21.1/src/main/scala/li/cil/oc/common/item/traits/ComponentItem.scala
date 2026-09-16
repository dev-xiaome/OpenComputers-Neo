package li.cil.oc.common.item.traits

import net.minecraft.network.chat.Component
import net.minecraft.world.{InteractionHand, InteractionResultHolder}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.level.Level

/** Adds the double-sneak gesture for resetting an item's component identity. */
trait ComponentItem extends SimpleItem {
  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] =
    player.getItemInHand(hand) match {
      case stack: ItemStack if player.isShiftKeyDown =>
        var resultStack = stack
        if (!world.isClientSide) {
          if (ComponentItem.registerSneakClick(player, hand, stack.getItem, world.getGameTime)) {
            // Recreate the stack so the reset returns a completely fresh item.
            resultStack = new ItemStack(stack.getItem, stack.getCount)
            player.setItemInHand(hand, resultStack)
            player.displayClientMessage(Component.literal("Item reset."), true)
          }
          else {
            player.displayClientMessage(Component.literal("Double click quickly to reset"), true)
          }
        }
        InteractionResultHolder.sidedSuccess(resultStack, world.isClientSide)

      case _ => super.use(world, player, hand)
    }
}

object ComponentItem {
  private val DoubleSneakClickWindowTicks = 6L
  private val lastSneakClicks = scala.collection.mutable.HashMap.empty[(java.util.UUID, InteractionHand, Item), Long]

  private def registerSneakClick(player: Player, hand: InteractionHand, item: Item, now: Long): Boolean = synchronized {
    val key = (player.getUUID, hand, item)
    val isDoubleClick = lastSneakClicks.get(key).exists(last => now - last > 0 && now - last <= DoubleSneakClickWindowTicks)

    if (isDoubleClick) lastSneakClicks.remove(key)
    else lastSneakClicks.update(key, now)

    isDoubleClick
  }
}
