package li.cil.oc.util

import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

import scala.language.implicitConversions

/**
 * `Level` / `BlockGetter` 的扩展。
 *
 * 1.21.1 迁移要点：
 *  - `World` → `Level`，`IBlockAccess` → `BlockGetter`
 *  - `getBlock(x, y, z)` → `getBlockState(pos).getBlock`（或直接用 `getBlockState(pos)`）
 *  - `getTileEntity` → `getBlockEntity`
 *  - `blockExists` → `isLoaded`
 *  - `isAirBlock` → `getBlockState(pos).isAir`
 *  - `setBlock(pos, block, meta, flags)` → `setBlock(pos, state, flags)`
 *  - `notifyBlockOfNeighborChange` / `notifyBlocksOfNeighborChange` → `updateNeighborsAt`
 *  - `playAuxSFX` → `levelEvent`
 *  - 方块 metadata 概念在 1.21.1 已被 `BlockState` 属性取代，相关方法见下方 TODO(标签)
 */
object ExtendedWorld {

  implicit def extendedBlockAccess(world: BlockGetter): ExtendedBlockAccess = new ExtendedBlockAccess(world)

  implicit def extendedWorld(world: Level): ExtendedWorld = new ExtendedWorld(world)

  class ExtendedBlockAccess(val world: BlockGetter) {
    def getBlockState(position: BlockPosition): BlockState = world.getBlockState(position.toChunkCoordinates)

    def getBlock(position: BlockPosition): Block = getBlockState(position).getBlock

    def getBlockMapColor(position: BlockPosition): Int =
      getBlockState(position).getMapColor(world, position.toChunkCoordinates).id

    /**
     * TODO(标签): 1.21.1 已移除方块 metadata，状态改由 `BlockState` 属性承载。
     * 这里固定返回 `0`，调用方应改为读取对应的 `Property`。
     */
    def getBlockMetadata(position: BlockPosition): Int = 0

    def getTileEntity(position: BlockPosition): BlockEntity = world.getBlockEntity(position.toChunkCoordinates)

    def getBlockEntity(position: BlockPosition): BlockEntity = getTileEntity(position)

    def getTileEntity(host: EnvironmentHost): BlockEntity = getTileEntity(BlockPosition(host))

    def isAirBlock(position: BlockPosition): Boolean = getBlockState(position).isAir

    /**
     * 旧版返回 (skyLight << 20) | (blockLight << 4) | (minBrightness << 4) 的打包亮度。
     * 1.21.1 对应 `BlockAndTintGetter#getMaxLocalRawBrightness(pos, minBrightness)`，
     * 已包含 `minBrightness` 参数，直接委托即可。
     */
    def getLightBrightnessForSkyBlocks(position: BlockPosition, minBrightness: Int): Int =
      world.getMaxLocalRawBrightness(position.toChunkCoordinates, minBrightness)
  }

  class ExtendedWorld(override val world: Level) extends ExtendedBlockAccess(world) {
    def blockExists(position: BlockPosition): Boolean = world.isLoaded(position.toChunkCoordinates)

    def breakBlock(position: BlockPosition, drops: Boolean = true): Boolean =
      world.destroyBlock(position.toChunkCoordinates, drops)

    def destroyBlockInWorldPartially(entityId: Int, position: BlockPosition, progress: Int): Unit =
      world.destroyBlockProgress(entityId, position.toChunkCoordinates, progress)

    def extinguishFire(player: Player, position: BlockPosition, side: Direction): Boolean =
      world.extinguishFire(player, position.toChunkCoordinates, side)

    /** 等价于旧版 `Block#getBlockHardness` → 1.21.1 的 `BlockState#getDestroySpeed`。 */
    def getBlockHardness(position: BlockPosition): Float =
      getBlockState(position).getDestroySpeed(world, position.toChunkCoordinates)

    /**
     * TODO(标签): 1.21.1 已移除 `Block#getHarvestLevel`，采掘等级改为数据驱动的
     * `BlockTags.INCORRECT_FOR_*_TOOL` / 工具组件，无直接等价物，固定返回 0。
     */
    def getBlockHarvestLevel(position: BlockPosition): Int = 0

    /**
     * TODO(标签): 1.21.1 已移除 `Block#getHarvestTool`，采掘工具改为 tag 驱动，
     * 无直接等价物，固定返回空字符串。
     */
    def getBlockHarvestTool(position: BlockPosition): String = ""

    // Passing `side` instead of `side.getOpposite` is *correct* here, because Minecraft.
    def computeRedstoneSignal(position: BlockPosition, side: Direction): Int = {
      val pos = position.toChunkCoordinates
      math.max(world.getSignal(pos.relative(side), side), world.getDirectSignalTo(pos.relative(side)))
    }

    /** 旧版 `isBlockProvidingPowerTo`：方块自身朝向 `side` 输出的直接信号。 */
    def isBlockProvidingPowerTo(position: BlockPosition, side: Direction): Int =
      world.getSignal(position.toChunkCoordinates, side)

    /** 旧版 `getIndirectPowerLevelTo`：来自 `side` 方向的间接（红石线）信号。 */
    def getIndirectPowerLevelTo(position: BlockPosition, side: Direction): Int =
      // TODO(标签): 1.21.1 无“间接能量等级”查询，用 `getDirectSignalTo` 近似（含直接与间接来源）。
      world.getDirectSignalTo(position.toChunkCoordinates)

    def markBlockForUpdate(position: BlockPosition): Unit = {
      // 1.21.1 中“通知客户端方块变化”等价于发送方块更新（UPDATE_CLIENTS），
      // 原 `markAndNotifyBlock` 需要区块引用，此处改用更直接的 `sendBlockUpdated`。
      val pos = position.toChunkCoordinates
      val state = world.getBlockState(pos)
      world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
    }

    def notifyBlockOfNeighborChange(position: BlockPosition, block: Block): Unit =
      world.updateNeighborsAt(position.toChunkCoordinates, block)

    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block): Unit =
      world.updateNeighborsAt(position.toChunkCoordinates, block)

    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, side: Direction): Unit =
      world.updateNeighborsAtExceptFromFacing(position.toChunkCoordinates, block, side)

    def playAuxSFX(id: Int, position: BlockPosition, data: Int): Unit =
      world.levelEvent(id, position.toChunkCoordinates, data)

    def playAuxSFX(player: Player, id: Int, position: BlockPosition, data: Int): Unit =
      world.levelEvent(player, id, position.toChunkCoordinates, data)

    def setBlock(position: BlockPosition, state: BlockState, flag: Int): Boolean =
      world.setBlock(position.toChunkCoordinates, state, flag)

    def setBlock(position: BlockPosition, state: BlockState): Boolean =
      world.setBlock(position.toChunkCoordinates, state, 3)

    def setBlock(position: BlockPosition, block: Block): Boolean =
      world.setBlock(position.toChunkCoordinates, block.defaultBlockState(), 3)

    /** 旧版带 metadata 的 `setBlock`：1.21.1 无 metadata，`metadata` 参数被忽略。 */
    def setBlock(position: BlockPosition, block: Block, metadata: Int, flag: Int): Boolean = {
      // TODO(标签): 1.21.1 的方块状态由 `BlockState` 属性决定，旧 `metadata` 参数被丢弃。
      world.setBlock(position.toChunkCoordinates, block.defaultBlockState(), flag)
    }

    def setBlockToAir(position: BlockPosition): Boolean =
      world.setBlock(position.toChunkCoordinates, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)

    def isSideSolid(position: BlockPosition, side: Direction): Boolean =
      world.getBlockState(position.toChunkCoordinates).isFaceSturdy(world, position.toChunkCoordinates, side)
  }

}
