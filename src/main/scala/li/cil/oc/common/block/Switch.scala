package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 交换机 / 集线器（原 1.7.10 `Switch`，右键打开组件连接配置界面）。
 *
 * 1.21.1 迁移要点：
 *  - 混入 [[traits.GUI]]，`guiType` 仍为 `GuiType.Switch`；
 *    TODO(GUI): `common/container` 与 `li.cil.oc.client.gui` 未移植，`GUI` trait 目前只
 *    返回「交互已消费」，等菜单层移植后改为 `player.openMenu(MenuProvider)`。
 *  - `createTileEntity` → [[SimpleBlockHooks.createBlockEntity]]，构造为
 *    `new tileentity.Switch(pos, state)`；
 *  - 原客户端侧 `Textures.Switch.iconSideActivity`（工作时的侧面贴图）删除，
 *    TODO(客户端): `li.cil.oc.client` 移植后用状态模型或 `BlockEntityRenderer` 恢复。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `SwitchTop`，
 * 北 / 南 / 西 / 东 = `SwitchSide`；活动时原为 `SwitchSideOn`。
 *
 * TODO(integration.util.NEI): 原构造里的 `NEI.hide(this)` 随 NEI 集成未移植而删除。
 */
class Switch(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) with traits.GUI {

  override def guiType = GuiType.Switch

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Switch(pos, state)
}
