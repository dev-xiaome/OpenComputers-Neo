package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level

/**
 * 「幻化物质」（原 `li.cil.oc.common.item.Chamelium`）：可食用（可配置）。
 *
 * 1.21.1 迁移要点：
 *  - `player.setItemInUse(stack, n)` → `Item#use` 返回 `consume`；1.21.1 由
 *    `Item#finishUsingItem`（`Delegate` 已转接到 `onEaten`）负责调用时机。
 *  - `Potion.invisibility.id` / `Potion.blindness.id` → `MobEffects.INVISIBILITY` / `BLINDNESS`
 *  - `world.isRemote` → `world.isClientSide`；`stack.stackSize` → `stack.getCount`
 */
class Chamelium(props: Item.Properties) extends Item(props) with traits.Delegate {

  override def use(world: Level, player: Player, hand: net.minecraft.world.InteractionHand)
  : net.minecraft.world.InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (Settings.get.chameliumEdible) {
      net.minecraft.world.InteractionResultHolder.consume(stack)
    }
    else {
      net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
    }
  }

  override def getItemUseAction(stack: ItemStack): UseAnim = UseAnim.EAT

  override def getMaxItemUseDuration(stack: ItemStack): Int = 32

  override def onEaten(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (!world.isClientSide) {
      player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0))
      player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0))
    }
    val result = stack.copy()
    result.shrink(1)
    result
  }
}
