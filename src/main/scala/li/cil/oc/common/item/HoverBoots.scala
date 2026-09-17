package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.client.renderer.item.HoverBootRenderer
import li.cil.oc.common.item.data.HoverBootsData
import li.cil.oc.util.ItemColorizer
import net.minecraft.world.effect.{MobEffectInstance, MobEffects}
import net.minecraft.world.entity.{Entity, EquipmentSlot}
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.{ArmorItem, ArmorMaterial, ArmorMaterials, ItemStack}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.{Blocks, LayeredCauldronBlock}


class HoverBoots(props: Properties) extends ArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.BOOTS, props) with traits.SimpleItem with traits.Chargeable {
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

  //@TODO replace with IItemRenderProperties
  //@OnlyIn(Dist.CLIENT)
  //override def getArmorModel[A <: HumanoidModel[_]](entityLiving: LivingEntity, itemStack: ItemStack, armorSlot: EquipmentSlot, _default: A): A = {
  //  if (armorSlot == slot) {
  //    HoverBootRenderer.lightColor = if (ItemColorizer.hasColor(itemStack)) ItemColorizer.getColor(itemStack) else 0x66DD55
  //    HoverBootRenderer.asInstanceOf[A]
  //  }
  //  else super.getArmorModel(entityLiving, itemStack, armorSlot, _default)
  //}

  // 1.21.1：Forge 的 `getArmorTexture(stack, entity, slot, subType: String): String` 换成了
  // `IItemExtension#getArmorTexture(stack, entity, slot, layer: ArmorMaterial.Layer, secondLayer: Boolean)`，
  // 返回值也从字符串变成 `ResourceLocation`。
  override def getArmorTexture(stack: ItemStack, entity: Entity, slot: EquipmentSlot, layer: ArmorMaterial.Layer, secondLayer: Boolean): net.minecraft.resources.ResourceLocation = {
    if (entity.level.isClientSide) HoverBootRenderer.texture
    else null
  }

  // 1.21.1：`ArmorItem#onArmorTick` 已被移除（护甲 tick 并入 `Item#inventoryTick`），
  // 因此把原来的减速逻辑搬到 `inventoryTick`，并自行判断这一格是不是脚部护甲。
  override def inventoryTick(stack: ItemStack, level: Level, entity: Entity, slot: Int, selected: Boolean): Unit = {
    super.inventoryTick(stack, level, entity, slot, selected)
    entity match {
      case player: Player if player.getItemBySlot(EquipmentSlot.FEET) eq stack =>
        if (!Settings.get.ignorePower && player.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null && getCharge(stack) == 0) {
          player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1))
        }
      case _ =>
    }
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

  // Contradictory as it may seem with the above, this avoids actual damage value changing.
  // 1.21.1：`Item#canBeDepleted` 已移除（可损耗性现在由 `MAX_DAMAGE` / `UNBREAKABLE` 组件决定），
  // 物品侧最接近的钩子是 `IItemExtension#isDamageable`。
  override def isDamageable(stack: ItemStack): Boolean = false

  override def setDamage(stack: ItemStack, damage: Int): Unit = {
    // Subtract energy when taking damage instead of actually damaging the item.
    charge(stack, -damage, simulate = false)

    // Set to 0 for old boots that may have been damaged before.
    super.setDamage(stack, 0)
  }
}
