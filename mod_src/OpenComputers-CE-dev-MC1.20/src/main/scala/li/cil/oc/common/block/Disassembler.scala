package li.cil.oc.common.block

import java.util
import li.cil.oc.Settings
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.util.Tooltip
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.{TooltipFlag => ITooltipFlag}
import net.minecraft.server.level.{ServerPlayer => ServerPlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.{Component => ITextComponent}
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}

import scala.collection.convert.ImplicitConversionsToScala._

class Disassembler(props: Properties) extends SimpleBlock(props) with traits.PowerAcceptor with traits.StateAware with traits.GUI with traits.Tickable {
  override protected def tooltipBody(stack: ItemStack, world: IBlockReader, tooltip: util.List[ITextComponent], advanced: ITooltipFlag): Unit = {
    for (curr <- Tooltip.get(getClass.getSimpleName.toLowerCase, (Settings.get.disassemblerBreakChance * 100).toInt.toString)) {
      tooltip.add(ITextComponent.literal(curr).setStyle(Tooltip.DefaultStyle))
    }
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.disassemblerRate

  override def openGui(player: ServerPlayerEntity, world: World, pos: BlockPos): Unit = world.getBlockEntity(pos) match {
    case te: blockentity.Disassembler => MenuTypes.openDisassemblerGui(player, te)
    case _ =>
  }

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Disassembler(pos, state)

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.DISASSEMBLER.get()
}
