package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.effect.{MobEffectInstance, MobEffects}
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.{Item, ItemStack, UseAnim}
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.{InteractionHand, InteractionResultHolder}
import net.neoforged.neoforge.common.extensions.IItemExtension

class Chamelium(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    if (Settings.get.chameliumEdible) {
      player.startUsingItem(hand)
    }

    InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
  }

  override def getUseAnimation(stack: ItemStack): UseAnim = UseAnim.EAT

  override def getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 32

  override def finishUsingItem(stack: ItemStack, level: Level, player: LivingEntity): ItemStack = {
    if (!level.isClientSide) {
      player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0))
      player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0))
    }
    stack.shrink(1)
    if (stack.getCount > 0) stack
    else ItemStack.EMPTY
  }
}
