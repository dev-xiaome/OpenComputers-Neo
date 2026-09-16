package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.block.property.PropertyCableConnection
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.block.CableHelper
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionHand
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.shapes.{CollisionContext, Shapes, VoxelShape}

class TapeDrive(props: Properties) extends SimpleBlock(props) with traits.Tickable with traits.GUI {
  registerDefaultState(defaultBlockState().setValue(PropertyRotatable.Facing, Direction.NORTH))

  override protected def createBlockStateDefinition(builder: StateDefinition.Builder[Block, BlockState]): Unit =
    builder.add(PropertyRotatable.Facing)

  override def getStateForPlacement(ctx: BlockPlaceContext): BlockState =
    defaultBlockState().setValue(PropertyRotatable.Facing, ctx.getHorizontalDirection)
  override def openGui(player: ServerPlayer, level: Level, pos: BlockPos): Unit = level.getBlockEntity(pos) match {
    case drive: blockentity.TapeDrive => MenuTypes.openTapeDriveGui(player, drive)
    case _ =>
  }
  override def newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = new blockentity.TapeDrive(pos, state)
  override def localOnBlockActivated(level: Level, pos: BlockPos, player: Player, hand: InteractionHand, held: ItemStack, side: Direction, x: Float, y: Float, z: Float): Boolean = level.getBlockEntity(pos) match {
    case drive: blockentity.TapeDrive if player.isCrouching =>
      // Inventory and playback state are server-owned. Returning success on
      // the client keeps the hand animation while avoiding ghost insertion.
      if (!level.isClientSide) drive.interact(player, held)
      true
    case _: blockentity.TapeDrive => super.localOnBlockActivated(level, pos, player, hand, held, side, x, y, z)
    case _ => false
  }
  @Deprecated
  override def neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, fromPos: BlockPos, moving: Boolean): Unit = {
    level.getBlockEntity(pos) match {
      case drive: blockentity.TapeDrive if !level.isClientSide => drive.onRedstone(level.hasNeighborSignal(pos))
      case _ =>
    }
    super.neighborChanged(state, level, pos, block, fromPos, moving)
  }
  override def getBlockEntityType = blockentity.BlockEntityTypes.TAPE_DRIVE.get()
}
