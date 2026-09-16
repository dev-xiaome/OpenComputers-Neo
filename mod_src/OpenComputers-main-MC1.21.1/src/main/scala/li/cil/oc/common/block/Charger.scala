package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.integration.util.Wrench
import li.cil.oc.server.PacketSender
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.{StateDefinition => StateContainer}
import net.minecraft.core.Direction
import net.minecraft.world.{InteractionHand => Hand}
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}

class Charger(props: Properties) extends RedstoneAware(props) with traits.PowerAcceptor with traits.StateAware with traits.GUI with traits.Tickable {
  protected override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]) =
    builder.add(PropertyRotatable.Facing)

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.chargerRate

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Charger => MenuTypes.openChargerGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Charger(pos, state)

  // ----------------------------------------------------------------------- //

  override def canConnectRedstone(state: BlockState, world: IBlockReader, pos: BlockPos, side: Direction): Boolean = true

  // ----------------------------------------------------------------------- //

  override def localOnBlockActivated(world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, heldItem: ItemStack, side: Direction, hitX: Float, hitY: Float, hitZ: Float) =
    if (Wrench.holdsApplicableWrench(player, pos)) world.getBlockEntity(pos) match {
      case charger: blockentity.Charger =>
        if (!world.isClientSide) {
          charger.invertSignal = !charger.invertSignal
          charger.chargeSpeed = 1.0 - charger.chargeSpeed
          PacketSender.sendChargerState(charger)
          Wrench.wrenchUsed(player, pos)
        }
        true
      case _ => false
    }
    else super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)

  @Deprecated
  override def neighborChanged(state: BlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos, b: Boolean): Unit = {
    world.getBlockEntity(pos) match {
      case charger: blockentity.Charger => charger.onNeighborChanged()
      case _ =>
    }
    super.neighborChanged(state, world, pos, block, fromPos, b)
  }

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.CHARGER.get()
}
