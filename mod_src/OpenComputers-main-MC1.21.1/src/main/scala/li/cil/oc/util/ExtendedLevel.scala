package li.cil.oc.util

import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.BlockGetter

import scala.language.implicitConversions
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Block
import net.minecraft.tags.BlockTags
import net.neoforged.neoforge.common.Tags

object ExtendedLevel {

  implicit def extendedBlockAccess(getter: BlockGetter): ExtendedBlockAccess = new ExtendedBlockAccess(getter)

  implicit def extendedLevel(level: Level): ExtendedLevel = new ExtendedLevel(level)

  class ExtendedBlockAccess(val getter: BlockGetter) {
    def getBlock(position: BlockPosition) = getter.getBlockState(position.toBlockPos).getBlock

    def getBlockMapColor(position: BlockPosition) = getBlockMetadata(position).getMapColor(getter, position.toBlockPos)

    def getBlockMetadata(position: BlockPosition) = getter.getBlockState(position.toBlockPos)

    def getBlockEntity(position: BlockPosition): BlockEntity = getter.getBlockEntity(position.toBlockPos)

    def getBlockEntity(host: EnvironmentHost): BlockEntity = getBlockEntity(BlockPosition(host))

    def isAirBlock(position: BlockPosition) = {
      // issue #4: may cause NPE in world.get
      position.world.get.isEmptyBlock(position.toBlockPos)
    }
  }

  class ExtendedLevel(val level: Level) extends ExtendedBlockAccess(level) {
    def blockExists(position: BlockPosition) = level.isLoaded(position.toBlockPos)

    def breakBlock(position: BlockPosition, drops: Boolean = true) = level.destroyBlock(position.toBlockPos, drops)

    def destroyBlockInWorldPartially(entityId: Int, position: BlockPosition, progress: Int) = level.destroyBlockProgress(entityId, position.toBlockPos, progress)

    def extinguishFire(player: Player, position: BlockPosition, side: Direction) = {
      val pos = position.toBlockPos
      val state = level.getBlockState(pos)
      if (state.is(BlockTags.FIRE)) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState, 3)
        true
      }
      else false
    }

    def getBlockHardness(position: BlockPosition) = level.getBlockState(position.toBlockPos).getDestroySpeed(level, position.toBlockPos)

    def getBlockHarvestLevel(position: BlockPosition): Int = {
      val state = position.world.get.getBlockState(position.toBlockPos)

      if (state.is(Tags.Blocks.NEEDS_NETHERITE_TOOL)) 4
      else if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) 3
      else if (state.is(BlockTags.NEEDS_IRON_TOOL)) 2
      else if (state.is(BlockTags.NEEDS_STONE_TOOL)) 1
      else 0
    }

    def getBlockHarvestTool(position: BlockPosition): String = {
      val state = position.world.get.getBlockState(position.toBlockPos)

      if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) "pickaxe"
      else if (state.is(BlockTags.MINEABLE_WITH_AXE)) "axe"
      else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) "shovel"
      else if (state.is(BlockTags.MINEABLE_WITH_HOE)) "hoe"
      else null
    }

    def computeRedstoneSignal(position: BlockPosition, side: Direction) = math.max(level.isBlockProvidingPowerTo(position.offset(side), side), level.getIndirectPowerLevelTo(position.offset(side), side))

    def isBlockProvidingPowerTo(position: BlockPosition, side: Direction) = level.getDirectSignal(position.toBlockPos, side)

    def getIndirectPowerLevelTo(position: BlockPosition, side: Direction) = level.getSignal(position.toBlockPos, side)

    def notifyBlockUpdate(pos: BlockPos): Unit = level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3)

    def notifyBlockUpdate(position: BlockPosition): Unit = level.sendBlockUpdated(position.toBlockPos, level.getBlockState(position.toBlockPos), level.getBlockState(position.toBlockPos), 3)

    def notifyBlockUpdate(position: BlockPosition, oldState: BlockState, newState: BlockState, flags: Int = 3): Unit = level.sendBlockUpdated(position.toBlockPos, oldState, newState, flags)

    def notifyBlockOfNeighborChange(position: BlockPosition, block: Block) = level.neighborChanged(position.toBlockPos, block, position.toBlockPos)

    @Deprecated
    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, updateObservers: Boolean) = level.updateNeighborsAt(position.toBlockPos, block)

    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, side: Direction) = level.updateNeighborsAtExceptFromFacing(position.toBlockPos, block, side)

    def playAuxSFX(id: Int, position: BlockPosition, data: Int) = level.levelEvent(id, position.toBlockPos, data)

    def setBlock(position: BlockPosition, block: Block) = level.setBlockAndUpdate(position.toBlockPos, block.defaultBlockState)

    @Deprecated
    def setBlock(position: BlockPosition, block: Block, metadata: Int, flag: Int) = {
      val states = block.getStateDefinition.getPossibleStates
      val state = if (metadata >= 0 && metadata < states.size) states.get(metadata) else block.defaultBlockState
      level.setBlock(position.toBlockPos, state, flag)
    }

    def setBlockToAir(position: BlockPosition) = level.setBlockAndUpdate(position.toBlockPos, Blocks.AIR.defaultBlockState)

    def isLoaded(position: BlockPosition) = level.isLoaded(position.toBlockPos)
  }

}
