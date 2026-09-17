package li.cil.oc.util

import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.{BlockAndTintGetter, BlockGetter, Level, LightLayer}
import net.minecraft.world.level.block.{BaseFireBlock, Block, LevelEvent}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

import scala.language.implicitConversions

/**
 * `Level` / `BlockGetter` 的扩展。
 *
 * 1.21.1 迁移要点：
 *  - `Level` → `Level`，`IBlockAccess` → `BlockGetter`
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
     * 旧版返回 `(skyLight << 20) | (blockLight << 4)` 的打包亮度：低 16 位是方块光、
     * 高 16 位是天空光，调用方普遍用 `brightness % 65536` / `brightness / 65536` 取分量。
     * <br>
     * 1.21.1 已没有这个打包方法（`BlockAndTintGetter#getRawBrightness` 只返回单值亮度），
     * 因此这里用 [[net.minecraft.world.level.LightLayer]] 分别取天空光 / 方块光后手工打包，
     * 位布局与 1.21.1 客户端的 `LightTexture.pack(blockLight, skyLight)` 完全一致；
     * 同时保留旧版“方块光不低于 `minBrightness`”的语义。
     * <br>
     * 注意：`getBrightness` 定义在 `BlockAndTintGetter` 上而非 `BlockGetter` 上，
     * 因此这里做一次类型判定；退化情况（理论上不会发生，`Level` 一定实现了该接口）
     * 只返回 `minBrightness` 对应的方块光。
     */
    def getLightBrightnessForSkyBlocks(position: BlockPosition, minBrightness: Int): Int = world match {
      case getter: BlockAndTintGetter =>
        val pos = position.toChunkCoordinates
        val sky = getter.getBrightness(LightLayer.SKY, pos)
        val block = math.max(getter.getBrightness(LightLayer.BLOCK, pos), minBrightness)
        (block << 4) | (sky << 20)
      case _ => minBrightness << 4
    }
  }

  class ExtendedWorld(override val world: Level) extends ExtendedBlockAccess(world) {
    def blockExists(position: BlockPosition): Boolean = world.isLoaded(position.toChunkCoordinates)

    def breakBlock(position: BlockPosition, drops: Boolean = true): Boolean =
      world.destroyBlock(position.toChunkCoordinates, drops)

    def destroyBlockInWorldPartially(entityId: Int, position: BlockPosition, progress: Int): Unit =
      world.destroyBlockProgress(entityId, position.toChunkCoordinates, progress)

    /**
     * 1.7.10 的 `World#extinguishFire(player, x, y, z, side)` 在 1.21.1 已被移除，
     * 这里按原语义手工实现：判定 `side` 方向相邻的方块是否为火，是则播放熄灭音效并移除。
     */
    def extinguishFire(player: Player, position: BlockPosition, side: Direction): Boolean = {
      val pos = position.toChunkCoordinates.relative(side)
      if (world.getBlockState(pos).getBlock.isInstanceOf[BaseFireBlock]) {
        world.levelEvent(player, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0)
        world.removeBlock(pos, false)
        true
      }
      else false
    }

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
