package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 运动传感器（原 1.7.10 `MotionSensor`）。
 *
 * 1.21.1 迁移要点：
 *  - `hasTileEntity` / `createTileEntity(world, metadata)` →
 *    [[SimpleBlockHooks.hasBlockEntity]]（默认 `true`）/ [[SimpleBlockHooks.createBlockEntity]]；
 *  - 构造为 `new tileentity.MotionSensor(pos, state)`；
 *  - 整套图标系统删除（`customTextures` 面序语义见下）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = `MotionSensorTop`，北 / 南 / 西 / 东 = `MotionSensorSide`。
 *
 * 原 1.7.10 未设置光照等级（也没有调用 `ModColoredLights`），因此保持默认 0。
 */
class MotionSensor(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.MotionSensor(pos, state)
}
