package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 换能器（原 1.7.10 `Transposer`，提供流体/物品转运组件的方块形态）。
 *
 * 1.21.1 迁移要点：
 *  - `hasTileEntity` / `createTileEntity` → `hasBlockEntity`（默认 `true`）/
 *    `createBlockEntity`，构造为 `new tileentity.Transposer(pos, state)`；
 *  - `isSideSolid`（原恒 `false`）在 1.21.1 由「碰撞形状 + 面坚固判定」取代，
 *    不再覆写，见 [[traits.SpecialBlock]] 的说明；
 *  - 原客户端侧 `Textures.Transposer.iconOn`（工作时换贴图）删除，
 *    TODO(客户端): `li.cil.oc.client` 移植后用状态模型或 `BlockEntityRenderer` 恢复。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = `TransposerTop`，北 / 南 / 西 / 东 = `TransposerSide`；
 * 工作时原为 `TransposerOn`。
 */
class Transposer(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Transposer(pos, state)
}
