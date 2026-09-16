package li.cil.oc.common.block.traits

import li.cil.oc.common.blockentity.traits.BaseBlockEntity
import li.cil.oc.common.blockentity.traits.{Tickable => TileEntityTickable}
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityTicker, BlockEntityType}
import net.minecraft.world.level.block.state.BlockState

trait Tickable extends EntityBlock {
  def getBlockEntityType: BlockEntityType[_ <: BlockEntity]

  override def getTicker[T <: BlockEntity](pLevel: Level, pState: BlockState, pBlockEntityType: BlockEntityType[T]): BlockEntityTicker[T] = {
    if (pBlockEntityType == getBlockEntityType) {
      (level: Level, pos: BlockPos, state: BlockState, blockEntity: T) => {
        blockEntity match {
          case tickable: TileEntityTickable => tickable.tick()
          case _ =>
        }
      }
    } else {
      null
    }
  }
}
