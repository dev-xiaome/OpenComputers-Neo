package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.util.Tooltip
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.network.chat.{Component => ITextComponent}
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.world.{InteractionHand => Hand}
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.item.{ItemStack, TooltipFlag => ITooltipFlag}
import net.minecraft.world.level.{BlockGetter => IBlockReader, Level => World}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.{BlockState, StateDefinition => StateContainer}
import net.minecraft.world.level.material.FluidState
import net.minecraftforge.common.extensions.IForgeBlock

import java.util
import scala.collection.convert.ImplicitConversionsToScala._

class Case(props: Properties, val tier: Int) extends RedstoneAware(props) with traits.PowerAcceptor with traits.StateAware with traits.GUI with traits.Tickable {
  protected override def createBlockStateDefinition(builder: StateContainer.Builder[Block, BlockState]): Unit =
    builder.add(PropertyRotatable.Facing, property.PropertyRunning.Running)

  // ----------------------------------------------------------------------- //

  override protected def tooltipBody(stack: ItemStack, world: IBlockReader, tooltip: util.List[ITextComponent], advanced: ITooltipFlag): Unit = {
    for (curr <- Tooltip.get(getClass.getSimpleName.toLowerCase, slots)) {
      tooltip.add(ITextComponent.literal(curr).setStyle(Tooltip.DefaultStyle))
    }
  }

  private def slots = tier match {
    case 0 => "2/1/1"
    case 1 => "2/2/2"
    case 2 | 3 => "3/2/3"
    case _ => "0/0/0"
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.caseRate(tier)

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Case if te.stillValid(player) => MenuTypes.openCaseGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Case(pos, state, tier)

  // ----------------------------------------------------------------------- //

  override def localOnBlockActivated(world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, heldItem: ItemStack, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    if (player.isCrouching) {
      if (!world.isClientSide) world.getBlockEntity(pos) match {
        case computer: blockentity.Case if !computer.machine.isRunning && computer.stillValid(player) => computer.machine.start()
        case _ =>
      }
      true
    }
    else super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
  }

  override def onDestroyedByPlayer(state: BlockState,
                               world: World,
                               pos: BlockPos,
                               player: PlayerEntity,
                               willHarvest: Boolean,
                               fluid: FluidState
                              ): Boolean = {
    Option(world.getBlockEntity(pos)) match {
      case Some(c: blockentity.Case) =>
        val playerName = player.getName.getString
        if (c.isCreative && (!player.isCreative || !c.canInteract(playerName))) {
          false
        } else {
          c.canInteract(playerName) && super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid)
        }
      case _ =>
        super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid)
    }
  }

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.CASE.get()
}
