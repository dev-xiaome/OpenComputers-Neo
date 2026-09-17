package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 装配机（原 1.7.10 `Assembler`）。
 *
 * 1.21.1 迁移要点：
 *  - 原 `ModColoredLights.setLightLevel(this, 0, 3, 5)`：彩色光源集成未移植，
 *    无彩色光源时原实现取 `max(0, 3, 5) = 5` 作为原版光照等级，这里保持同值，
 *    见 [[Assembler.properties]]。
 *    TODO(integration.coloredlights): 彩色光源集成移植后再补上彩色发光。
 *  - `isBlockSolid` / `isSideSolid`（原只把上下面视为实心）在 1.21.1 已被
 *    「碰撞形状 + 面坚固判定」取代（见 [[traits.SpecialBlock]] 的说明），不再覆写。
 *  - `getIcon` / `customTextures` / `Textures.Assembler.*`（装配中 / 通电贴图）全部删除，
 *    面纹理改由模型 JSON 指定；按状态换贴图需要客户端参与。
 *    TODO(客户端): `li.cil.oc.client` 移植后用状态化模型或 `BlockEntityRenderer` 恢复。
 *  - `hasTileEntity` / `createTileEntity` → `createBlockEntity`（`hasBlockEntity` 默认 `true`）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `AssemblerTop`，
 * 北 / 南 / 西 / 东 = `AssemblerSide`。
 * 原状态贴图：`AssemblerSideAssembling`（装配中）、`AssemblerSideOn` / `AssemblerTopOn`（通电）。
 */
class Assembler(properties: BlockBehaviour.Properties = Assembler.properties())
  extends SimpleBlock(properties) with traits.SpecialBlock with traits.PowerAcceptor with traits.StateAware with traits.GUI {

  override def energyThroughput = Settings.get.assemblerRate

  override def guiType = GuiType.Assembler

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Assembler(pos, state)
}

object Assembler {
  /**
   * 装配机方块属性。
   *
   * 原 `ModColoredLights.setLightLevel(this, 0, 3, 5)` 在没有彩色光源时取
   * `max(0, 3, 5) = 5`，这里保持同样的原版光照等级。
   */
  def properties(): BlockBehaviour.Properties =
    SimpleBlock.properties().lightLevel((_: BlockState) => 5)
}
