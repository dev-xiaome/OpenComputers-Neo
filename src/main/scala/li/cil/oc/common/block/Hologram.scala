package li.cil.oc.common.block

import java.util
import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.util.Tooltip
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.{TooltipFlag => ITooltipFlag}
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.shapes.{CollisionContext => ISelectionContext}
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.{Shapes => VoxelShapes}
import net.minecraft.network.chat.{Component => ITextComponent}
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.{BlockGetter => IBlockReader}

import scala.collection.convert.ImplicitConversionsToScala._

class Hologram(props: Properties, val tier: Int) extends SimpleBlock(props) with traits.Tickable {
  val shape = VoxelShapes.box(0, 0, 0, 1, 0.5, 1)

  // ----------------------------------------------------------------------- //

  override def getShape(state: BlockState, world: IBlockReader, pos: BlockPos, ctx: ISelectionContext): VoxelShape = shape

  // ----------------------------------------------------------------------- //

  override protected def tooltipBody(stack: ItemStack, world: IBlockReader, tooltip: util.List[ITextComponent], advanced: ITooltipFlag): Unit = {
    for (curr <- Tooltip.get(getClass.getSimpleName.toLowerCase() + tier)) {
      tooltip.add(ITextComponent.literal(curr).setStyle(Tooltip.DefaultStyle))
    }
  }

  // ----------------------------------------------------------------------- //

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Hologram(pos, state, tier)

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.HOLOGRAM.get()
}
