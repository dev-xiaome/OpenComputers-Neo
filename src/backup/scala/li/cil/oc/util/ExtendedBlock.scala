package li.cil.oc.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.AABB

import scala.language.implicitConversions

/**
 * `Block` / `BlockState` 的扩展。
 *
 * 1.21.1 迁移要点：
 *  - `Block` 是单例，不再持有状态；所有状态相关查询改为基于 `BlockState`
 *  - `block.isAir(world, x, y, z)` → `state.isAir()`
 *  - `block.isReplaceable(world, x, y, z)` → `state.canBeReplaced()`
 *  - `block.getMapColor(meta)` → `state.getMapColor(world, pos)`
 *  - `block.getComparatorInputOverride(...)` → `state.getAnalogOutputSignal(level, pos)`
 *  - `setBlockBoundsBasedOnState` / `getSelectedBoundingBoxFromPool` /
 *    `getCollisionBoundingBoxFromPool` 在新版没有直接等价物（形状改由 `VoxelShape`
 *    提供），见下方 TODO(渲染)
 */
object ExtendedBlock {

  /** 旧版签名兼容的隐式转换：`block.isAir(position)` 形式。 */
  implicit def extendedBlock(block: Block): ExtendedBlock = new ExtendedBlock(block)

  /** 新版推荐写法：直接对 `BlockState` 做查询。 */
  implicit def extendedBlockState(state: BlockState): ExtendedBlockState = new ExtendedBlockState(state)

  class ExtendedBlock(val block: Block) {
    private def stateAt(position: BlockPosition): BlockState =
      position.world.get.getBlockState(position.toChunkCoordinates)

    def isAir(position: BlockPosition): Boolean = stateAt(position).isAir

    def isReplaceable(position: BlockPosition): Boolean = stateAt(position).canBeReplaced

    def getMapColor(position: BlockPosition): MapColor =
      stateAt(position).getMapColor(position.world.get, position.toChunkCoordinates)

    def getComparatorInputOverride(position: BlockPosition, side: net.minecraft.core.Direction): Int = {
      // 注意：1.21.1 的比较器输出只与方块自身状态有关，不再按 `side` 区分（`side` 参数仅为兼容旧签名保留）。
      // 调用方原先传入 `side.getOpposite`，新版语义下不再需要。
      stateAt(position).getAnalogOutputSignal(position.world.get, position.toChunkCoordinates)
    }

    def defaultState: BlockState = block.defaultBlockState()

    /**
     * TODO(渲染): 1.21.1 已移除 `Block#setBlockBoundsBasedOnState`，
     * 方块形状由 `BlockState#getShape` 返回的 `VoxelShape` 决定，暂保留空实现。
     */
    def setBlockBoundsBasedOnState(position: BlockPosition): Unit = ()

    /**
     * TODO(渲染): 1.21.1 已移除 `Block#getSelectedBoundingBoxFromPool`。
     * 近似等价物为 `state.getShape(world, pos).bounds()`，需要调用方自行提供
     * `CollisionContext`，此处暂返回 `null`。
     */
    def getSelectedBoundingBoxFromPool(position: BlockPosition): AABB = null

    /**
     * TODO(渲染): 1.21.1 已移除 `Block#getCollisionBoundingBoxFromPool`。
     * 近似判断为 `state.getCollisionShape(world, pos).isEmpty`，此处暂返回 `null`。
     */
    def getCollisionBoundingBoxFromPool(position: BlockPosition): AABB = null
  }

  class ExtendedBlockState(val state: BlockState) {
    def block: Block = state.getBlock

    def isAir: Boolean = state.isAir

    def isReplaceable: Boolean = state.canBeReplaced

    def getMapColor(world: BlockGetter, position: BlockPos): MapColor = state.getMapColor(world, position)

    def getComparatorInputOverride(level: Level, position: BlockPos): Int = state.getAnalogOutputSignal(level, position)
  }

}
