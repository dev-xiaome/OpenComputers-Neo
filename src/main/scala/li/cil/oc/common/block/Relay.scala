package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 中继器（原 1.7.10 `Relay`，把有线网络与无线网络连起来的方块）。
 *
 * 1.21.1 迁移要点：
 *  - 混入 [[traits.GUI]]（`guiType` = `GuiType.Relay`）与 [[traits.PowerAcceptor]]
 *    （`energyThroughput` = `Settings.get.accessPointRate`）；
 *  - `createTileEntity` → [[SimpleBlockHooks.createBlockEntity]]，构造为
 *    `new tileentity.Relay(pos, state)`；
 *  - 原客户端侧 `Textures.Switch.iconSideActivity`（工作时侧面贴图）删除，
 *    TODO(客户端): `li.cil.oc.client` 移植后用状态模型或 `BlockEntityRenderer` 恢复。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `SwitchTop`，
 * 北 / 南 / 西 / 东 = `SwitchSide`；活动时原为 `SwitchSideOn`。
 */
class Relay(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) with traits.GUI with traits.PowerAcceptor {

  override def guiType = GuiType.Relay

  override def energyThroughput = Settings.get.accessPointRate

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Relay(pos, state)
}
