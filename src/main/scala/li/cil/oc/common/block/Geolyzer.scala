package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 地质分析仪（原 1.7.10 `Geolyzer`）。
 *
 * 1.21.1 迁移要点：
 *  - 原 `ModColoredLights.setLightLevel(this, 3, 1, 1)`：彩色光源集成未移植，
 *    无彩色光源时取 `max(3, 1, 1) = 3` 作为原版光照等级，这里以构造属性
 *    `.lightLevel(_ => 3)` 表达（见 [[Geolyzer.properties]]）。
 *    TODO(integration.coloredlights): 彩色光源集成移植后再补上按通道发光。
 *  - 原客户端侧 `Textures.Geolyzer.iconTopOn`（工作时顶面换贴图）删除，
 *    需要按方块实体状态换模型，TODO(客户端): `li.cil.oc.client` 移植后用状态模型或
 *    `BlockEntityRenderer` 恢复。
 *  - `hasTileEntity` / `createTileEntity` → `hasBlockEntity` / `createBlockEntity`，
 *    构造为 `new tileentity.Geolyzer(pos, state)`。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `GeolyzerTop`，
 * 北 / 南 / 西 / 东 = `GeolyzerSide`；工作时顶面原为 `GeolyzerTopOn`。
 */
class Geolyzer(properties: BlockBehaviour.Properties = Geolyzer.properties())
  extends SimpleBlock(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Geolyzer(pos, state)
}

object Geolyzer {
  /** 原 1.7.10 在构造里设置的光照等级（无彩色光源时取 `max(3, 1, 1)`）。 */
  def properties(): BlockBehaviour.Properties =
    SimpleBlock.properties().lightLevel(_ => 3)
}
