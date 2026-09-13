package li.cil.oc.common.block

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import li.cil.oc.util.Color
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.util.IIcon
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class Case(val tier: Int) extends RedstoneAware with traits.PowerAcceptor with traits.StateAware with traits.GUI {
  private val iconsOn = new Array[IIcon](6)

  // ----------------------------------------------------------------------- //

  override protected def customTextures = Array(
    Some("CaseTop"),
    Some("CaseTop"),
    Some("CaseBack"),
    Some("CaseFront"),
    Some("CaseSide"),
    Some("CaseSide")
  )

  override def registerBlockIcons(iconRegister: IIconRegister) = {
    super.registerBlockIcons(iconRegister)
    System.arraycopy(icons, 0, iconsOn, 0, icons.length)
    iconsOn(Direction.NORTH.ordinal) = iconRegister.registerIcon(Settings.resourceDomain + ":CaseBackOn")
    iconsOn(Direction.WEST.ordinal) = iconRegister.registerIcon(Settings.resourceDomain + ":CaseSideOn")
    iconsOn(Direction.EAST.ordinal) = iconsOn(Direction.WEST.ordinal)
  }

  override def getIcon(world: IBlockAccess, x: Int, y: Int, z: Int, worldSide: Direction, localSide: Direction) = {
    if (world.getTileEntity(x, y, z) match {
      case computer: tileentity.Case => computer.isRunning
      case _ => false
    }) iconsOn(localSide.ordinal)
    else getIcon(localSide.ordinal(), 0)
  }

  @SideOnly(Dist.CLIENT)
  override def getRenderColor(metadata: Int) = Color.byTier(tier)

  // ----------------------------------------------------------------------- //

  override def rarity(stack: ItemStack) = Rarity.byTier(tier)

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get(getClass.getSimpleName, slots))
  }

  private def slots = tier match {
    case 0 => "2/1/1"
    case 1 => "2/2/2"
    case 2 | 3 => "3/2/3"
    case _ => "0/0/0"
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.caseRate(tier)

  override def guiType = GuiType.Case

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Case(tier)

  // ----------------------------------------------------------------------- //

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player,
                                side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    if (player.isSneaking) {
      if (!world.isRemote) world.getTileEntity(x, y, z) match {
        case computer: tileentity.Case if !computer.machine.isRunning && computer.isUseableByPlayer(player) => computer.machine.start()
        case _ =>
      }
      true
    }
    else super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ)
  }

  override def removedByPlayer(world: Level, player: Player, x: Int, y: Int, z: Int, willHarvest: Boolean): Boolean =
    world.getTileEntity(x, y, z) match {
      case c: tileentity.Case =>
        if (c.isCreative && (!player.capabilities.isCreativeMode || !c.canInteract(player.getCommandSenderName))) false
        else c.canInteract(player.getCommandSenderName) && super.removedByPlayer(world, player, x, y, z, willHarvest)
      case _ => super.removedByPlayer(world, player, x, y, z, willHarvest)
    }
}
