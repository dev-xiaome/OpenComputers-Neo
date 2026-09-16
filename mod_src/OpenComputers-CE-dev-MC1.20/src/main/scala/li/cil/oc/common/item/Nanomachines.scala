package li.cil.oc.common.item

import java.util

import com.google.common.base.Strings
import li.cil.oc.api
import li.cil.oc.common.item.data.NanomachineData
import li.cil.oc.common.nanomachines.ControllerImpl
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
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.UseAnim
import net.minecraft.world.entity.LivingEntity

class Nanomachines(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  @OnlyIn(Dist.CLIENT)
  override def appendHoverText(stack: ItemStack, level: Level, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, level, tooltip, flag)
    if (stack.hasTag) {
      val data = new NanomachineData(stack)
      if (!Strings.isNullOrEmpty(data.uuid)) {
        tooltip.add(Component.literal("§8" + data.uuid.substring(0, 13) + "...§7"))
      }
    }
  }

  override def use(stack: ItemStack, level: Level, player: Player): InteractionResultHolder[ItemStack] = {
    player.startUsingItem(if (player.getItemInHand(InteractionHand.MAIN_HAND) == stack) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND)
    new InteractionResultHolder(InteractionResult.sidedSuccess(level.isClientSide), stack)
  }

  override def getUseAnimation(stack: ItemStack): UseAnim = UseAnim.EAT

  override def getUseDuration(stack: ItemStack): Int = 32

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
