package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import li.cil.oc.util.Tooltip
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 拆解机（原 1.7.10 `Disassembler`）。
 *
 * 1.21.1 迁移要点：
 *  - `tooltipBody(metadata, stack, player, tooltip, advanced)` →
 *    [[SimpleBlockHooks.tooltipBody]]，去掉 `metadata` 参数（1.21.1 无方块 metadata）。
 *  - `getIcon` / `customTextures` / `Textures.Disassembler.*` 全部删除，面纹理改由模型 JSON 指定。
 *  - `hasTileEntity` / `createTileEntity` → `createBlockEntity`（`hasBlockEntity` 默认 `true`）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `DisassemblerTop`，
 * 北 / 南 / 西 / 东 = `DisassemblerSide`。
 * 原状态贴图：`DisassemblerSideOn`、`DisassemblerTopOn`。
 */
class Disassembler(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) with traits.PowerAcceptor with traits.StateAware with traits.GUI {

  // ----------------------------------------------------------------------- //

  override def tooltipBody(stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get(getClass.getSimpleName, (Settings.get.disassemblerBreakChance * 100).toInt.toString))
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.disassemblerRate

  override def guiType = GuiType.Disassembler

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Disassembler(pos, state)
}
