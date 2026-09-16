package li.cil.oc.common.block

import java.util
import li.cil.oc.common.blockentity
import li.cil.oc.integration.Mods
import li.cil.oc.util.Tooltip
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.item.{TooltipFlag => ITooltipFlag}
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.{Component => ITextComponent}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}
import net.minecraft.world.level.block.state.BlockState

import scala.collection.convert.ImplicitConversionsToScala._

class Redstone(props: Properties) extends RedstoneAware(props) {
  override protected def tooltipTail(stack: ItemStack, world: IBlockReader, tooltip: util.List[ITextComponent], advanced: ITooltipFlag): Unit = {
    super.tooltipTail(stack, world, tooltip, advanced)
    // todo more generic way for redstone mods to provide lines
    if (Mods.ProjectRedTransmission.isModAvailable) {
      for (curr <- Tooltip.get("redstonecard.ProjectRed")) tooltip.add(ITextComponent.literal(curr).setStyle(Tooltip.DefaultStyle))
    }
  }

  // ----------------------------------------------------------------------- //

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Redstone(pos, state)
}
