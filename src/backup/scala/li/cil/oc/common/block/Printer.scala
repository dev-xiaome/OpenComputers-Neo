package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 3D 打印机（原 1.7.10 `Printer`，提供 `printer3d` 组件）。
 *
 * 1.21.1 迁移要点：
 *  - 混入 [[traits.SpecialBlock]] / [[traits.StateAware]]（比较器按工作状态输出）/
 *    [[traits.GUI]]（`guiType` = `GuiType.Printer`）；
 *  - 原 `isBlockSolid` / `isSideSolid` 只把**下面**视为实心：1.21.1 已由「碰撞形状 +
 *    面坚固判定」取代，不再覆写（见 [[traits.SpecialBlock]] 的说明）；
 *  - `hasTileEntity` / `createTileEntity` → `hasBlockEntity` / `createBlockEntity`，
 *    构造为 `new tileentity.Printer(pos, state)`；
 *  - 整套图标系统删除。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `PrinterTop`，
 * 北 / 南 / 西 / 东 = `PrinterSide`。
 */
class Printer(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) with traits.SpecialBlock with traits.StateAware with traits.GUI {

  override def guiType = GuiType.Printer

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Printer(pos, state)
}
