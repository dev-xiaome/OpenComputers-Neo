package li.cil.oc.common.block

import java.util

import li.cil.oc.Settings
import li.cil.oc.common.tileentity
import li.cil.oc.util.{Rarity, Tooltip}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.{CollisionContext, VoxelShape}

/**
 * 全息投影仪（原 1.7.10 `Hologram`，等级 1 / 2）。
 *
 * 1.21.1 迁移要点：
 *  - 等级由方块实例表达（`hologram1` / `hologram2`，见规范 §1.2.1），[[tier]] 为 `val`：
 *    方块实体需要从方块反查等级。
 *  - `setBlockBounds(0, 0, 0, 1, 0.5f, 1)` → [[SimpleBlockHooks.blockShape]]
 *    （1.21.1 用 `VoxelShape`，`setBlockBounds` 已不存在）；碰撞形状默认与选中形状一致，
 *    因此投影仪只有下半格有碰撞，与原版一致。
 *  - `ModColoredLights.setLightLevel(this, 15, 15, 15)`：彩色光源集成（`integration.coloredlights`）
 *    未移植，等价的原版语义是「发光等级 15」，见 [[Hologram.properties]]。
 *    TODO(integration.coloredlights): 彩色光源集成移植后补上彩色发光。
 *  - `isBlockSolid` / `isSideSolid`（原只把下面视为实心）：1.21.1 已由「碰撞形状 + 面坚固判定」
 *    取代（见 [[traits.SpecialBlock]] 的说明），不再覆写。
 *  - `shouldSideBeRendered` → 同名钩子（参数改为 `(state, adjacentState, side)`）。
 *  - `getIcon` / `customTextures` / `registerBlockIcons` 全部删除，面纹理改由模型 JSON 指定。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `HologramTop1` / `HologramTop2`（按 tier），
 * 北 / 南 / 西 / 东 = `HologramSide`。
 */
class Hologram(val tier: Int, properties: BlockBehaviour.Properties = Hologram.properties())
  extends SimpleBlock(properties) with traits.SpecialBlock {

  // ----------------------------------------------------------------------- //
  // 形状
  // ----------------------------------------------------------------------- //

  override def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    shape(new AABB(0, 0, 0, 1, 0.5, 1))

  override def shouldSideBeRendered(state: BlockState, adjacentState: BlockState, side: Direction): Boolean =
    super.shouldSideBeRendered(state, adjacentState, side) || side == Direction.UP

  // ----------------------------------------------------------------------- //
  // 提示
  // ----------------------------------------------------------------------- //

  override def rarity(stack: ItemStack) = Rarity.byTier(tier)

  override def tooltipBody(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get(getClass.getSimpleName + tier))
  }

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Hologram(pos, state)
}

object Hologram {
  /**
   * 投影仪方块属性。
   *
   * 原 `if (Settings.get.hologramLight) ModColoredLights.setLightLevel(this, 15, 15, 15)`：
   * 没有彩色光源集成时，等价于把原版光照等级设为 15。
   */
  def properties(): BlockBehaviour.Properties = {
    val properties = SimpleBlock.nonOccluding()
    if (Settings.get.hologramLight) properties.lightLevel((_: BlockState) => 15) else properties
  }
}
