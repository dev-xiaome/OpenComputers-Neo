package li.cil.oc.common.item

import li.cil.oc.api
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.UseAnim
import net.minecraft.world.item.ItemStack
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.level.Level

class Acid(val parent: Delegator) extends traits.Delegate {
  override def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = {
    player.setItemInUse(stack, getMaxItemUseDuration(stack))
    stack
  }

  override def getItemUseAction(stack: ItemStack): EnumAction = EnumAction.drink

  override def getMaxItemUseDuration(stack: ItemStack): Int = 32

  override def onEaten(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (!world.isRemote) {
      player.addPotionEffect(new PotionEffect(Potion.blindness.id, 200))
      player.addPotionEffect(new PotionEffect(Potion.poison.id, 100))
      player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 600))
      player.addPotionEffect(new PotionEffect(Potion.confusion.id, 1200))
      player.addPotionEffect(new PotionEffect(Potion.field_76443_y.id, 2000))

      // Remove nanomachines if installed.
      api.Nanomachines.uninstallController(player)
    }
    stack.stackSize -= 1
    if (stack.stackSize > 0) stack
    else null
  }
}
