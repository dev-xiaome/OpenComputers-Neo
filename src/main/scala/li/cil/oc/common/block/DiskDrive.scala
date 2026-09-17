package li.cil.oc.common.block

import java.util

import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.blockentity
import li.cil.oc.integration.Mods
import li.cil.oc.util.Tooltip
import net.minecraft.world.level.block.state.BlockBehaviour.{Properties => Properties}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.{TooltipFlag => ITooltipFlag}
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.{StateDefinition => StateContainer}
import net.minecraft.core.Direction
import net.minecraft.world.{InteractionHand => Hand}
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.{Component => ITextComponent}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}

import scala.collection.convert.ImplicitConversionsToScala._

class DiskDrive(props: Properties) extends SimpleBlock(props) with traits.GUI {
  protected override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]) =
    builder.add(PropertyRotatable.Facing)

  // ----------------------------------------------------------------------- //

  override protected def tooltipTail(stack: ItemStack, world: IBlockReader, tooltip: util.List[ITextComponent], flag: ITooltipFlag): Unit = {
    super.tooltipTail(stack, world, tooltip, flag)
    if (Mods.ComputerCraft.isModAvailable) {
      for (curr <- Tooltip.get(getClass.getSimpleName + ".CC")) tooltip.add(ITextComponent.literal(curr).setStyle(Tooltip.DefaultStyle))
    }
  }

  // ----------------------------------------------------------------------- //

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.DiskDrive => MenuTypes.openDiskDriveGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.DiskDrive(pos, state)

  // ----------------------------------------------------------------------- //

  override def hasAnalogOutputSignal(state: BlockState): Boolean = true

  override def getAnalogOutputSignal(state: BlockState, world: World, pos: BlockPos): Int =
    world.getBlockEntity(pos) match {
      case drive: blockentity.DiskDrive if !drive.getItem(0).isEmpty => 15
      case _ => 0
    }

  // ----------------------------------------------------------------------- //

  override def localOnBlockActivated(world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, heldItem: ItemStack, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    // Behavior: sneaking -> Insert[+Eject], not sneaking -> GUI.
    if (player.isCrouching) world.getBlockEntity(pos) match {
      case drive: blockentity.DiskDrive =>
        val isDiskInDrive = drive.getItem(0) != null
        val isHoldingDisk = drive.canPlaceItem(0, heldItem)
        if (isDiskInDrive) {
          if (!world.isClientSide) {
            drive.dropSlot(0, 1, Option(drive.facing))
          }
        }
        if (isHoldingDisk) {
          // Insert the disk.
          drive.setItem(0, heldItem.split(1))
        }
        isDiskInDrive || isHoldingDisk
      case _ => false
    }
    else super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
  }
}
