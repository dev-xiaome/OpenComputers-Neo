package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 能量分配器（原 1.7.10 `PowerDistributor`，把能量平均分给各个相连的网络）。
 *
 * 1.21.1 迁移要点：
 *  - 原 `ModColoredLights.setLightLevel(this, 5, 5, 3)`：彩色光源集成未移植，
 *    无彩色光源时取 `max(5, 5, 3) = 5` 作为原版光照等级（见 [[PowerDistributor.properties]]）。
 *    TODO(integration.coloredlights): 彩色光源集成移植后再补上按通道发光。
 *  - 原 `Textures.PowerDistributor.iconSideOn` / `iconTopOn`（工作时换贴图）删除，
 *    TODO(客户端): `li.cil.oc.client` 移植后用状态模型或 `BlockEntityRenderer` 恢复。
 *  - `hasTileEntity` / `createTileEntity` → `hasBlockEntity` / `createBlockEntity`，
 *    构造为 `new tileentity.PowerDistributor(pos, state)`。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `PowerDistributorTop`，
 * 北 / 南 / 西 / 东 = `PowerDistributorSide`；工作时原为 `…SideOn` / `…TopOn`。
 */
class PowerDistributor(properties: BlockBehaviour.Properties = PowerDistributor.properties())
  extends SimpleBlock(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.PowerDistributor(pos, state)
}

object PowerDistributor {
  /** 原 1.7.10 在构造里设置的光照等级（无彩色光源时取 `max(5, 5, 3)`）。 */
  def properties(): BlockBehaviour.Properties =
    SimpleBlock.properties().lightLevel(_ => 5)
}
