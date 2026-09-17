package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.Settings.DebugCardAccess
import li.cil.oc.common.item.data.DebugCardData
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「调试卡」（原 `li.cil.oc.common.item.DebugCard`）。
 *
 * 1.21.1 迁移要点：
 *  - `player.getCommandSenderName` → `player.getScoreboardName`
 *  - `player.addChatComponentMessage(String)` →
 *    `player.displayClientMessage(Component.literal(...), false)`
 *  - `player.swingItem()` → `player.swing(hand)`
 *  - `server.component.DebugCard.AccessContext` 已改为 [[li.cil.oc.Settings.AccessContext]]
 *    （见 `common/item/data/DebugCardData.scala` 的说明）
 */
class DebugCard(props: Item.Properties) extends Item(props) with traits.Delegate {

  override protected def tooltipExtended(stack: ItemStack, tooltip: java.util.List[String]): Unit = {
    super.tooltipExtended(stack, tooltip)
    val data = new DebugCardData(stack)
    data.access.foreach(access => tooltip.add(s"§8${access.player}§r"))
  }

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (!world.isClientSide && player.isShiftKeyDown) {
      val data = new DebugCardData(stack)
      val name = player.getScoreboardName

      if (data.access.exists(_.player == name)) data.access = None
      else {
        val nonce = Settings.get.debugCardAccess match {
          case wl: DebugCardAccess.Whitelist => wl.nonce(name) match {
            case Some(n) => n
            case None =>
              player.displayClientMessage(
                Component.literal("§cYou are not whitelisted to use debug card"), false)
              player.swing(hand)
              return InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
          }
          case _ => ""
        }
        data.access = Some(Settings.AccessContext(name, nonce))
      }

      data.save(stack)
      player.swing(hand)
    }
    InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
  }
}
