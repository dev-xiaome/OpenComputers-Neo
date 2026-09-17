package li.cil.oc.util

import net.minecraft.world.level.block.Block
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.state.BlockState

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
    def getComparatorInputOverride(position: BlockPosition, side: Direction) = {
      // 1.21.1：BlockBehaviour#getAnalogOutputSignal 仍是 protected，
      // 公开入口改到了 BlockState 上（BlockStateBase#getAnalogOutputSignal(Level, BlockPos)）。
      val level = position.world.get
      level.getBlockState(position.toBlockPos).getAnalogOutputSignal(level, position.toBlockPos)
    }
  }

  // NeoForge 1.21.1 移除了 IFluidBlock，随之取消 ExtendedFluidBlock 扩展
  // （世界中的流体统一由 LiquidBlock 承载，相关逻辑见 FluidUtils）。

}
