package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.client.renderer.item.HoverBootRenderer
import li.cil.oc.common.item.data.HoverBootsData
import li.cil.oc.util.ItemColorizer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.effect.{MobEffectInstance, MobEffects}
import net.minecraft.world.entity.{Entity, EquipmentSlot}
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.{ArmorItem, ArmorMaterials, ItemStack}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.{Blocks, LayeredCauldronBlock}
import net.neoforged.neoforge.common.extensions.IItemExtension


class HoverBoots(props: Properties) extends ArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.BOOTS, props) with traits.SimpleItem with traits.Chargeable with IItemExtension {
  override def maxCharge(stack: ItemStack): Double = Settings.get.bufferHoverBoots

  override def getCharge(stack: ItemStack): Double =
    new HoverBootsData(stack).charge

  override def setCharge(stack: ItemStack, amount: Double): Unit = {
    val data = new HoverBootsData(stack)
    data.charge = math.min(maxCharge(stack), math.max(0, amount))
    data.saveData(stack)
  }

  override def canCharge(stack: ItemStack): Boolean = true

  override def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double = {
    val data = new HoverBootsData(stack)
    traits.Chargeable.applyCharge(amount, data.charge, Settings.get.bufferHoverBoots, used => if (!simulate) {
      data.charge += used
      data.saveData(stack)
    })
  }

  override def onEntityItemUpdate(stack: ItemStack, entity: ItemEntity): Boolean = {
    if (entity != null && entity.level != null && !entity.level.isClientSide && ItemColorizer.hasColor(stack)) {
      val pos = entity.blockPosition
      val state = entity.level.getBlockState(pos)
      if (state.getBlock == Blocks.CAULDRON) {
        val level = state.getValue(LayeredCauldronBlock.LEVEL).toInt
        if (level > 0) {
          ItemColorizer.removeColor(stack)
          entity.level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, Int.box(level - 1)), 3)
          return true
        }
      }
    }
    super.onEntityItemUpdate(stack, entity)
  }

  override def isBarVisible(stack: ItemStack): Boolean = true

  override def getBarWidth(stack: ItemStack): Int = {
    val data = new HoverBootsData(stack)
    val ratio = data.charge / Settings.get.bufferHoverBoots
    Math.round(ratio * 13.0f).toInt
  }

  override def getMaxDamage(stack: ItemStack): Int = Settings.get.bufferHoverBoots.toInt

  // Always show energy bar.
  override def isDamaged(stack: ItemStack): Boolean = true

  override def setDamage(stack: ItemStack, damage: Int): Unit = {
    // Subtract energy when taking damage instead of actually damaging the item.
    charge(stack, -damage, simulate = false)

    // Set to 0 for old boots that may have been damaged before.
    super.setDamage(stack, 0)
  }
}
