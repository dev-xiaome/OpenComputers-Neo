package li.cil.oc.util

import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

import scala.language.implicitConversions

object ExtendedWorld {

  implicit def extendedBlockAccess(world: IBlockAccess): ExtendedBlockAccess = new ExtendedBlockAccess(world)

  implicit def extendedWorld(world: Level): ExtendedWorld = new ExtendedWorld(world)

  class ExtendedBlockAccess(val world: IBlockAccess) {
    def getBlock(position: BlockPosition) = world.getBlock(position.x, position.y, position.z)

    def getBlockMapColor(position: BlockPosition) = getBlock(position).getMapColor(getBlockMetadata(position))

    def getBlockMetadata(position: BlockPosition) = world.getBlockMetadata(position.x, position.y, position.z)

    def getTileEntity(position: BlockPosition): BlockEntity = world.getTileEntity(position.x, position.y, position.z)

    def getTileEntity(host: EnvironmentHost): BlockEntity = getTileEntity(BlockPosition(host))

    def isAirBlock(position: BlockPosition) = world.isAirBlock(position.x, position.y, position.z)

    def getLightBrightnessForSkyBlocks(position: BlockPosition, minBrightness: Int) = world.getLightBrightnessForSkyBlocks(position.x, position.y, position.z, minBrightness)
  }

  class ExtendedWorld(override val world: Level) extends ExtendedBlockAccess(world) {
    def blockExists(position: BlockPosition) = world.blockExists(position.x, position.y, position.z)

    def breakBlock(position: BlockPosition, drops: Boolean = true) = world.func_147480_a(position.x, position.y, position.z, drops)

    def destroyBlockInWorldPartially(entityId: Int, position: BlockPosition, progress: Int) = world.destroyBlockInWorldPartially(entityId, position.x, position.y, position.z, progress)

    def extinguishFire(player: Player, position: BlockPosition, side: Direction) = world.extinguishFire(player, position.x, position.y, position.z, side.ordinal)

    def getBlockHardness(position: BlockPosition) = getBlock(position).getBlockHardness(world, position.x, position.y, position.z)

    def getBlockHarvestLevel(position: BlockPosition) = getBlock(position).getHarvestLevel(getBlockMetadata(position))

    def getBlockHarvestTool(position: BlockPosition) = getBlock(position).getHarvestTool(getBlockMetadata(position))

    // Passing `side` instead of `side.getOpposite` is *correct* here, because Minecraft.
    def computeRedstoneSignal(position: BlockPosition, side: Direction) = math.max(world.isBlockProvidingPowerTo(position.offset(side), side), world.getIndirectPowerLevelTo(position.offset(side), side))

    def isBlockProvidingPowerTo(position: BlockPosition, side: Direction) = world.isBlockProvidingPowerTo(position.x, position.y, position.z, side.ordinal)

    def getIndirectPowerLevelTo(position: BlockPosition, side: Direction) = world.getIndirectPowerLevelTo(position.x, position.y, position.z, side.ordinal)

    def markBlockForUpdate(position: BlockPosition) = world.markBlockForUpdate(position.x, position.y, position.z)

    def notifyBlockOfNeighborChange(position: BlockPosition, block: Block) = world.notifyBlockOfNeighborChange(position.x, position.y, position.z, block)

    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block) = world.notifyBlocksOfNeighborChange(position.x, position.y, position.z, block)

    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, side: Direction) = world.notifyBlocksOfNeighborChange(position.x, position.y, position.z, block, side.ordinal)

    def playAuxSFX(id: Int, position: BlockPosition, data: Int) = world.playAuxSFX(id, position.x, position.y, position.z, data)

    def setBlock(position: BlockPosition, block: Block) = world.setBlock(position.x, position.y, position.z, block)

    def setBlock(position: BlockPosition, block: Block, metadata: Int, flag: Int) = world.setBlock(position.x, position.y, position.z, block, metadata, flag)

    def setBlockToAir(position: BlockPosition) = world.setBlockToAir(position.x, position.y, position.z)

    def isSideSolid(position: BlockPosition, side: Direction) = world.isSideSolid(position.x, position.y, position.z, side)
  }

}
