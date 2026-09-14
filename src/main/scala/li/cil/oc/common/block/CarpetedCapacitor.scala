package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 地毯式电容（原 1.7.10 `CarpetedCapacitor`，可以站上去的电容，会伤害站在上面的生物）。
 *
 * 1.21.1 迁移要点：只覆写方块实体构造，其余行为（比较器输出、邻居变化重算容量、光照）
 * 全部继承自 [[Capacitor]]。生物伤害逻辑在方块实体 `tileentity.CarpetedCapacitor` 里，
 * 由 tick 驱动（该方块实体 `canUpdate = true`）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `CarpetCapacitorTop`，
 * 北 / 南 / 西 / 东 = `CapacitorSide`。
 */
class CarpetedCapacitor(properties: BlockBehaviour.Properties = Capacitor.properties())
  extends Capacitor(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.CarpetedCapacitor(pos, state)
}
