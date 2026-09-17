package li.cil.oc.common.block

import java.util

import li.cil.oc.common.tileentity
import li.cil.oc.util.Tooltip
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 红石方块（原 1.7.10 `Redstone`，等价于外置红石卡）。
 *
 * 1.21.1 迁移要点：
 *  - 继承 [[RedstoneAware]]（红石输入/输出语义全部在那）；
 *  - `createTileEntity` → [[SimpleBlockHooks.createBlockEntity]]，构造为
 *    `new tileentity.Redstone(pos, state)`；
 *  - 整套图标系统删除（`customTextures` 六面各不相同，面序语义见下）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = `RedstoneBottom`，上 = `RedstoneTop`，北 = `RedstoneNorth`，南 = `RedstoneSouth`，
 * 西 = `RedstoneWest`，东 = `RedstoneEast`。
 *
 * ==降级说明==
 * TODO(integration): 原 tooltip 尾部按 `Mods.ProjectRedTransmission` / `Mods.RedLogic` /
 * `Mods.MineFactoryReloaded` 是否可用追加「已支持捆绑红石」说明。这三个第三方集成在
 * 1.21.1 都不再移植，因此改为**无条件**显示这三行（本地化键 `RedstoneCard.*` 均存在于
 * 语言文件中）；等对应驱动移植后请恢复 `isAvailable` 判断。
 */
class Redstone(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Redstone(pos, state)

  // ----------------------------------------------------------------------- //

  override def tooltipTail(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipTail(stack, player, tooltip, advanced)
    tooltip.addAll(Tooltip.get("RedstoneCard.ProjectRed"))
    tooltip.addAll(Tooltip.get("RedstoneCard.RedLogic"))
    tooltip.addAll(Tooltip.get("RedstoneCard.RedNet"))
  }
}
