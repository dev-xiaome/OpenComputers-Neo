package li.cil.oc.common.block

import li.cil.oc.client.gui
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.InteractionResult
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.Level
import net.neoforged.api.distmarker.{Dist, OnlyIn}

class Waypoint(props: Properties) extends RedstoneAware(props) with traits.Tickable {
  protected override def createBlockStateDefinition(builder: StateDefinition.Builder[Block, BlockState]) =
    builder.add(PropertyRotatable.Pitch, PropertyRotatable.Yaw)

  // ----------------------------------------------------------------------- //

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Waypoint(pos, state)

  // ----------------------------------------------------------------------- //

  override def useWithoutItem(state: BlockState, world: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult = {
    if (!player.isCrouching) {
      if (world.isClientSide) world.getBlockEntity(pos) match {
        case t: blockentity.Waypoint => showGui(t)
        case _ =>
      }
      InteractionResult.sidedSuccess(world.isClientSide)
    }
    else super.useWithoutItem(state, world, pos, player, hitResult)
  }

  @OnlyIn(Dist.CLIENT)
  private def showGui(t: blockentity.Waypoint): Unit = {
    Minecraft.getInstance.pushGuiLayer(new gui.Waypoint(t))
  }

  override def getValidRotations(world: Level, pos: BlockPos): Array[Direction] =
    world.getBlockEntity(pos) match {
      case waypoint: blockentity.Waypoint =>
        Direction.values.filter {
          d => d != waypoint.facing && d != waypoint.facing.getOpposite
        }
      case _ => super.getValidRotations(world, pos)
    }

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.WAYPOINT.get()
}
