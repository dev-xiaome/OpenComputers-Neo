package li.cil.oc.common.block

import cpw.mods.fml.relauncher.{Side, SideOnly}

import java.util
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity
import li.cil.oc.util.Color
import li.cil.oc.util.ItemColorizer
import li.cil.oc.util.ItemCosts
import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.util.StatCollector
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class Item(value: Block) extends ItemBlock(value) {
  setHasSubtypes(true)

  def block = field_150939_a

  override def addInformation(stack: ItemStack, player: Player, tooltip: util.List[_], advanced: Boolean): Unit = {
    super.addInformation(stack, player, tooltip, advanced)
    (block, tooltip) match {
      case (simple: SimpleBlock, lines: util.List[String]@unchecked) =>
        simple.addInformation(getMetadata(stack.getItemDamage), stack, player, lines, advanced)

        if (KeyBindings.showMaterialCosts) {
          ItemCosts.addTooltip(stack, lines)
        }
        else {
          lines.add(StatCollector.translateToLocalFormatted(
            Settings.namespace + "tooltip.MaterialCosts",
            KeyBindings.getKeyBindingName(KeyBindings.materialCosts)))
        }
      case _ =>
    }
  }

  override def getRarity(stack: ItemStack) = block match {
    case simple: SimpleBlock => simple.rarity(stack)
    case _ => EnumRarity.common
  }

  override def getMetadata(itemDamage: Int) = itemDamage

  override def getItemStackDisplayName(stack: ItemStack): String = {
    if (api.Items.get(stack) == api.Items.get(Constants.BlockName.Print)) {
      val data = new PrintData(stack)
      data.label.getOrElse(super.getItemStackDisplayName(stack))
    }
    else super.getItemStackDisplayName(stack)
  }

  override def getUnlocalizedName = block match {
    case simple: SimpleBlock => simple.getUnlocalizedName
    case _ => Settings.namespace + "tile"
  }

  @SideOnly(Dist.CLIENT)
  override def getColorFromItemStack(stack: ItemStack, v: Int) = {
    if (api.Items.get(stack) == api.Items.get(Constants.BlockName.Cable)) {
      if (ItemColorizer.hasColor(stack)) {
        ItemColorizer.getColor(stack)
      }
      else Color.LightGray
    }
    else super.getColorFromItemStack(stack, v)
  }

  override def isBookEnchantable(a: ItemStack, b: ItemStack) = false

  override def placeBlockAt(stack: ItemStack, player: Player, world: Level, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float, metadata: Int) = {
    // When placing robots in creative mode, we have to copy the stack
    // manually before it's placed to ensure different component addresses
    // in the different robots, to avoid interference of screens e.g.
    val needsCopying = player.capabilities.isCreativeMode && api.Items.get(stack) == api.Items.get(Constants.BlockName.Robot)
    val stackToUse = if (needsCopying) new RobotData(stack).copyItemStack() else stack
    if (super.placeBlockAt(stackToUse, player, world, x, y, z, side, hitX, hitY, hitZ, metadata)) {
      // If it's a rotatable block try to make it face the player.
      world.getTileEntity(x, y, z) match {
        case keyboard: tileentity.Keyboard =>
          keyboard.setFromEntityPitchAndYaw(player)
          keyboard.setFromFacing(Direction.getOrientation(side))
        case rotatable: tileentity.traits.Rotatable =>
          rotatable.setFromEntityPitchAndYaw(player)
          if (!rotatable.validFacings.contains(rotatable.pitch)) {
            rotatable.pitch = rotatable.validFacings.headOption.getOrElse(Direction.NORTH)
          }
          if (!rotatable.isInstanceOf[tileentity.RobotProxy]) {
            rotatable.invertRotation()
          }
        case _ => // Ignore.
      }
      true
    }
    else false
  }
}