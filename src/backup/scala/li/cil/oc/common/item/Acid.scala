package li.cil.oc.common.item

import li.cil.oc.api
import net.minecraft.world.effect.{MobEffectInstance, MobEffects}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.{Item, ItemStack, UseAnim}
import net.minecraft.world.level.Level

/**
 * 「酸液」（原 `li.cil.oc.common.item.Acid`）。
 *
 * 1.7.10 → 1.21.1：
 *  - `player.setItemInUse(stack, n)` → [[net.minecraft.world.item.Item#use]] 返回
 *    `InteractionResultHolder.consume`（1.21.1 用返回值表示「开始使用」，不再手动设置）
 *  - `Potion.blindness.id` 等 → [[net.minecraft.world.effect.MobEffects]] 的注册对象
 *  - `onEaten` 的「消耗一个」由 [[li.cil.oc.common.item.traits.Delegate#finishUsingItem]] 转接
 */
class Acid(props: Item.Properties) extends Item(props) with traits.Delegate {

  override protected def tooltipName: Option[String] = None

  override def use(world: Level, player: Player, hand: net.minecraft.world.InteractionHand)
  : net.minecraft.world.InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    net.minecraft.world.InteractionResultHolder.consume(stack)
  }

  override def getItemUseAction(stack: ItemStack): UseAnim = UseAnim.DRINK

  override def getMaxItemUseDuration(stack: ItemStack): Int = 32

  override def onEaten(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (!world.isClientSide) {
      player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200))
      player.addEffect(new MobEffectInstance(MobEffects.POISON, 100))
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600))
      player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 1200))
      player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 2000))

      // Remove nanomachines if installed.
      api.Nanomachines.uninstallController(player)
    }
    // 1.21.1 由 `finishUsingItem` 的调用方负责消耗物品，这里只需返回缩小后的堆叠。
    val result = stack.copy()
    result.shrink(1)
    result
  }
}
