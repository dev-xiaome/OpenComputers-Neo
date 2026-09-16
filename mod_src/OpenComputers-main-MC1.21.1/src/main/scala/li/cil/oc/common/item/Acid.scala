package li.cil.oc.common.item

import li.cil.oc.api
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.neoforged.neoforge.common.extensions.IItemExtension

class Acid(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    player.startUsingItem(hand)
    InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
  }

  override def getUseAnimation(stack: ItemStack): UseAnim = UseAnim.DRINK

  override def getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 32

  override def finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack = {
    entity match {
      case player: Player =>
        if (!level.isClientSide) {
          player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200))
          player.addEffect(new MobEffectInstance(MobEffects.POISON, 100))
          player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600))
          player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 1200))
          player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 2000))

          // Remove nanomachines if installed.
          api.Nanomachines.uninstallController(player)
        }
        stack.shrink(1)
        if (stack.getCount > 0) stack
        else ItemStack.EMPTY
      case _ => stack
    }
  }
}
