package li.cil.oc.common.item

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.item.HoverBootRenderer
import li.cil.oc.common.item.data.HoverBootsData
import li.cil.oc.util.ItemColorizer
import net.minecraft.client.model.ModelBiped
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.util.IIcon
import net.minecraft.util.Mth
import net.minecraft.world.level.Level

class HoverBoots extends ItemArmor(ItemArmor.ArmorMaterial.DIAMOND, 0, 3) with traits.SimpleItem with traits.Chargeable {
  setNoRepair()

  override def getRarity(stack: ItemStack): EnumRarity = EnumRarity.uncommon

  override def maxCharge(stack: ItemStack) = Settings.get.bufferHoverBoots

  override def getCharge(stack: ItemStack): Double =
    new HoverBootsData(stack).charge

  override def setCharge(stack: ItemStack, amount: Double): Unit = {
    val data = new HoverBootsData(stack)
    data.charge = math.min(maxCharge(stack), math.max(0, amount))
    data.save(stack)
  }

  override def canCharge(stack: ItemStack): Boolean = true

  override def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double = {
    val data = new HoverBootsData(stack)
    if (amount < 0) {
      val remainder = math.min(0, data.charge + amount)
      if (!simulate) {
        data.charge = math.max(0, data.charge + amount)
        data.save(stack)
      }
      remainder
    }
    else {
      val remainder = -math.min(0, Settings.get.bufferHoverBoots - (data.charge + amount))
      if (!simulate) {
        data.charge = math.min(Settings.get.bufferHoverBoots, data.charge + amount)
        data.save(stack)
      }
      remainder
    }
  }

  @SideOnly(Dist.CLIENT)
  override def getArmorModel(entityLiving: LivingEntity, itemStack: ItemStack, armorSlot: Int): ModelBiped = {
    if (armorSlot == armorType) {
      HoverBootRenderer.lightColor = if (ItemColorizer.hasColor(itemStack)) ItemColorizer.getColor(itemStack) else 0x66DD55
      HoverBootRenderer
    }
    else super.getArmorModel(entityLiving, itemStack, armorSlot)
  }

  override def getArmorTexture(stack: ItemStack, entity: Entity, slot: Int, subType: String): String = {
    if (entity.worldObj.isRemote) HoverBootRenderer.texture.toString
    else null
  }

  override def onArmorTick(world: Level, player: Player, stack: ItemStack): Unit = {
    super.onArmorTick(world, player, stack)
    if (!Settings.get.ignorePower && player.getActivePotionEffect(Potion.moveSlowdown) == null && getCharge(stack) == 0) {
      player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.getId, 20, 1))
    }
  }

  override def onEntityItemUpdate(entity: ItemEntity): Boolean = {
    if (entity != null && entity.worldObj != null && !entity.worldObj.isRemote && ItemColorizer.hasColor(entity.getEntityItem)) {
      val x = Mth.floor_double(entity.posX)
      val y = Mth.floor_double(entity.posY)
      val z = Mth.floor_double(entity.posZ)
      if (entity.worldObj.getBlock(x, y, z) == Blocks.cauldron) {
        val meta = entity.worldObj.getBlockMetadata(x, y, z)
        if (meta > 0) {
          ItemColorizer.removeColor(entity.getEntityItem)
          entity.worldObj.setBlockMetadataWithNotify(x, y, z, meta - 1, 3)
          return true
        }
      }
    }
    super.onEntityItemUpdate(entity)
  }

  @SideOnly(Dist.CLIENT)
  override def registerIcons(ir: IIconRegister): Unit = {
    this.itemIcon = ir.registerIcon(this.getIconString)
    Textures.HoverBoots.lightOverlay = ir.registerIcon(this.getIconString + "Light")
  }

  @SideOnly(Dist.CLIENT)
  override def requiresMultipleRenderPasses(): Boolean = true

  @SideOnly(Dist.CLIENT)
  override def getIconFromDamageForRenderPass(meta: Int, pass: Int): IIcon = if (pass == 1) Textures.HoverBoots.lightOverlay else super.getIconFromDamageForRenderPass(meta, pass)

  override def getColorFromItemStack(itemStack: ItemStack, pass: Int): Int = {
    if (pass == 1) {
      return if (ItemColorizer.hasColor(itemStack)) ItemColorizer.getColor(itemStack) else 0x66DD55
    }
    super.getColorFromItemStack(itemStack, pass)
  }

  override def getDisplayDamage(stack: ItemStack): Int = {
    val data = new HoverBootsData(stack)
    (Settings.get.bufferHoverBoots * (1 - data.charge / Settings.get.bufferHoverBoots)).toInt
  }

  override def getMaxDamage(stack: ItemStack): Int = Settings.get.bufferHoverBoots.toInt

  // Always show energy bar.
  override def isDamaged(stack: ItemStack): Boolean = true

  // Contradictory as it may seem with the above, this avoids actual damage value changing.
  override def isDamageable: Boolean = false

  override def setDamage(stack: ItemStack, damage: Int): Unit = {
    // Subtract energy when taking damage instead of actually damaging the item.
    charge(stack, -damage, simulate = false)

    // Set to 0 for old boots that may have been damaged before.
    super.setDamage(stack, 0)
  }
}
