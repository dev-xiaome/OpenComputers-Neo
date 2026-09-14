package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 电容（原 1.7.10 `Capacitor`，能量缓存方块）。
 *
 * 1.21.1 迁移要点：
 *  - 原 `ModColoredLights.setLightLevel(this, 5, 5, 5)`：彩色光源集成未移植，
 *    无彩色光源时取 `max(5, 5, 5) = 5` 作为原版光照等级（见 [[Capacitor.properties]]）。
 *    TODO(integration.coloredlights): 彩色光源集成移植后再补上按通道发光。
 *  - `hasComparatorInputOverride` / `getComparatorInputOverride` →
 *    [[SimpleBlockHooks.providesAnalogOutput]] / [[SimpleBlockHooks.analogOutputSignal]]：
 *    原实现按 `node.localBuffer / node.localBufferSize` 输出 0..15；1.21.1 里
 *    `localBufferSize` 可能为 0（`ignorePower` 或尚未重算容量），这里加了除零保护，
 *    并只在服务端求值（原实现也判断了 `!world.isRemote`）。
 *  - 原 `updateTick`（随机 tick 周期性 `notifyBlocksOfNeighborChange`）、
 *    `setTickRandomly(true)` 与 `tickRate` 删除：1.21.1 的比较器输出由原版在查询时求值，
 *    邻居通知由方块实体在容量变化时发出，不再需要随机 tick。
 *  - `onNeighborBlockChange` → 同名钩子，邻居变化时重算容量。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `CapacitorTop`，
 * 北 / 南 / 西 / 东 = `CapacitorSide`。
 */
class Capacitor(properties: BlockBehaviour.Properties = Capacitor.properties())
  extends SimpleBlock(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Capacitor(pos, state)

  // ----------------------------------------------------------------------- //
  // 比较器输出
  // ----------------------------------------------------------------------- //

  override def providesAnalogOutput: Boolean = true

  override def analogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int =
    level.getBlockEntity(pos) match {
      case capacitor: tileentity.Capacitor if !level.isClientSide && capacitor.node != null =>
        val size = capacitor.node.localBufferSize
        if (size > 0) math.round(15 * capacitor.node.localBuffer / size).toInt
        else 0
      case _ => 0
    }

  // ----------------------------------------------------------------------- //
  // 邻居变化
  // ----------------------------------------------------------------------- //

  override def onNeighborBlockChange(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block): Unit =
    level.getBlockEntity(pos) match {
      case capacitor: tileentity.Capacitor => capacitor.recomputeCapacity()
      case _ =>
    }
}

object Capacitor {
  /** 原 1.7.10 在构造里设置的光照等级（无彩色光源时取 `max(5, 5, 5)`）。 */
  def properties(): BlockBehaviour.Properties =
    SimpleBlock.properties().lightLevel(_ => 5)
}
