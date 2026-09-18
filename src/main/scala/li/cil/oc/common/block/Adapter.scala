package li.cil.oc.common.block

import com.mojang.serialization.MapCodec
import li.cil.oc.common.block.Adapter.CODEC
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.integration.util.Wrench
import net.minecraft.world.level.block.state.BlockBehaviour.{Properties, simpleCodec}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.minecraft.world.{InteractionHand => Hand}
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{LevelReader => IWorldReader}
import net.minecraft.world.level.{Level => World}

class Adapter(props: Properties) extends SimpleBlock(props) with traits.GUI with traits.Tickable {
  override def codec(): MapCodec[Adapter] = CODEC

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Adapter => MenuTypes.openAdapterGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Adapter(pos, state)

  // ----------------------------------------------------------------------- //

  @Deprecated
  override def neighborChanged(state: BlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos, b: Boolean): Unit =
    world.getBlockEntity(pos) match {
      case adapter: blockentity.Adapter => adapter.neighborChanged()
      case _ => // Ignore.
    }

  override def onNeighborChange(state: BlockState, world: IWorldReader, pos: BlockPos, neighbor: BlockPos) =
    world.getBlockEntity(pos) match {
      case adapter: blockentity.Adapter =>
        // TODO can we just pass the blockpos?
        val side =
          if (neighbor == (pos.below():BlockPos)) Direction.DOWN
          else if (neighbor == (pos.above():BlockPos)) Direction.UP
          else if (neighbor == (pos.north():BlockPos)) Direction.NORTH
          else if (neighbor == (pos.south():BlockPos)) Direction.SOUTH
          else if (neighbor == (pos.west():BlockPos)) Direction.WEST
          else if (neighbor == (pos.east():BlockPos)) Direction.EAST
          else throw new IllegalArgumentException("not a neighbor") // TODO wat
        adapter.neighborChanged(side)
      case _ => // Ignore.
    }

  override def localOnBlockActivated(world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, heldItem: ItemStack, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    if (Wrench.holdsApplicableWrench(player, pos)) {
      val sideToToggle = if (player.isCrouching) side.getOpposite else side
      world.getBlockEntity(pos) match {
        case adapter: blockentity.Adapter =>
          if (!world.isClientSide) {
            val oldValue = adapter.openSides(sideToToggle.ordinal())
            adapter.setSideOpen(sideToToggle, !oldValue)
          }
          true
        case _ => false
      }
    }
    else super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
  }

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.ADAPTER.get()
}

object Adapter {
  final val CODEC = simpleCodec(new Adapter(_))
}
