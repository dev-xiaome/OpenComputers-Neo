package li.cil.oc.common.item

import com.google.common.base.Strings
import li.cil.oc.api
import li.cil.oc.common.item.data.NanomachineData
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.{InteractionHand, InteractionResultHolder}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.{Item, ItemStack, TooltipFlag, UseAnim}
import net.minecraft.world.item.Item.{Properties, TooltipContext}
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.extensions.IItemExtension

import java.util

class Nanomachines(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    if (stack.has(DataComponents.CUSTOM_DATA)) {
      val data = new NanomachineData(stack)
      if (!Strings.isNullOrEmpty(data.uuid)) {
        tooltip.add(Component.literal("§8" + data.uuid.substring(0, 13) + "...§7"))
      }
    }
  }

  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    player.startUsingItem(hand)
    InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
  }

  override def getUseAnimation(stack: ItemStack): UseAnim = UseAnim.EAT

  override def getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 32

  override def finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack = {
    entity match {
      case player: Player =>
        if (!level.isClientSide) {
          val data = new NanomachineData(stack)

          // Re-install to get new address, make sure we're configured.
          api.Nanomachines.uninstallController(player)
          api.Nanomachines.installController(player) match {
            case controller: ControllerImpl =>
              data.configuration match {
                case Some(nbt) =>
                  if (!Strings.isNullOrEmpty(data.uuid)) {
                    controller.uuid = data.uuid
                  }
                  controller.configuration.loadData(nbt)
                case _ => controller.reconfigure()
              }
            case controller => controller.reconfigure() // Huh.
          }
        }
        stack.shrink(1)
        if (stack.getCount > 0) stack
        else ItemStack.EMPTY
      case _ => stack
    }
  }
}
