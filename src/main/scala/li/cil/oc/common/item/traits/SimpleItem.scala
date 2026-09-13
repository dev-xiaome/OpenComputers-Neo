package li.cil.oc.common.item.traits

import java.util
import java.util.Random

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.CreativeTab
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.tileentity
import li.cil.oc.util.ItemCosts
import li.cil.oc.util.Tooltip
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.util.WeightedRandomChestContent
import net.minecraft.world.level.Level
import net.minecraftforge.common.ChestGenHooks

trait SimpleItem extends Item {
  setCreativeTab(CreativeTab)
  setTextureName(Settings.resourceDomain + ":" + getClass.getSimpleName)

  def createItemStack(amount: Int = 1) = new ItemStack(this, amount)

  override def isBookEnchantable(stack: ItemStack, book: ItemStack) = false

  override def getChestGenBase(chest: ChestGenHooks, rnd: Random, original: WeightedRandomChestContent) = original

  override def doesSneakBypassUse(world: Level, x: Int, y: Int, z: Int, player: Player) = {
    world.getTileEntity(x, y, z) match {
      case drive: tileentity.DiskDrive => true
      case _ => super.doesSneakBypassUse(world, x, y, z, player)
    }
  }

  @SideOnly(Dist.CLIENT)
  override def addInformation(stack: ItemStack, player: Player, tooltip: util.List[_], advanced: Boolean): Unit = {
    val tt = tooltip.asInstanceOf[util.List[String]]
    tt.addAll(Tooltip.get(getClass.getSimpleName))

    if (ItemCosts.hasCosts(stack)) {
      if (KeyBindings.showMaterialCosts) {
        ItemCosts.addTooltip(stack, tt)
      }
      else {
        tt.add(Localization.localizeImmediately(
          Settings.namespace + "tooltip.MaterialCosts",
          KeyBindings.getKeyBindingName(KeyBindings.materialCosts)))
      }
    }
    if (stack.hasTagCompound && stack.getTagCompound.contains(Settings.namespace + "data")) {
      val data = stack.getTagCompound.getCompound(Settings.namespace + "data")
      if (data.contains("node") && data.getCompound("node").contains("address")) {
        tt.add("§8" + data.getCompound("node").getString("address").substring(0, 13) + "...§7")
      }
    }
  }
}
