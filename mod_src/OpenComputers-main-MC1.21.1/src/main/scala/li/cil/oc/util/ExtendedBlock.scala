package li.cil.oc.util

import net.minecraft.world.level.block.{Block, LiquidBlock}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

import scala.language.implicitConversions

object ExtendedBlock {

  implicit def extendedBlock(block: Block): ExtendedBlock = new ExtendedBlock(block)

  class ExtendedBlock(val block: Block) {
    @Deprecated
    def isAir(position: BlockPosition) = position.world.get.isEmptyBlock(position.toBlockPos)
    
    @Deprecated
    def isReplaceable(position: BlockPosition) = block.defaultBlockState.is(BlockTags.REPLACEABLE)

    @Deprecated
    def getBlockHardness(position: BlockPosition) = position.world.get.getBlockState(position.toBlockPos).getDestroySpeed(position.world.get, position.toBlockPos)

    @Deprecated
    def getComparatorInputOverride(position: BlockPosition, side: Direction) = position.world.get.getBlockState(position.toBlockPos).getAnalogOutputSignal(position.world.get, position.toBlockPos)
  }

//  implicit def extendedFluidBlock(block: LiquidBlock): ExtendedFluidBlock = new ExtendedFluidBlock(block)
//

}
