package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.{Level, LevelReader}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.BlockHitResult

/**
 * 适配器 / 「多插座」（原 1.7.10 `Adapter`，方块实体实现 `api.internal.Adapter`）。
 *
 * 1.21.1 迁移要点：
 *  - `hasTileEntity` / `createTileEntity(world, metadata)` → 方块实体走
 *    [[SimpleBlockHooks.createBlockEntity]]，且 `hasBlockEntity` 默认为 `true`，无需覆写；
 *    构造改为 `new tileentity.Adapter(pos, state)`（等级由方块本身表达，见规范 §1.2.1）。
 *  - `onNeighborBlockChange` / `onNeighborChange` → 同名钩子，参数统一为 `(state, level, pos, ...)`；
 *    对方块实体 `neighborChanged()` / `neighborChanged(side)` 的调用原样保留
 *    （适配器靠它感知相邻方块出现/消失）。
 *  - `ForgeDirection` → `Direction`：原 `sides` 表里的 `ForgeDirection.UNKNOWN` 表示
 *    「该偏移不对应任何面」，1.21.1 没有 `UNKNOWN`，这里用 `None` 表达同样语义
 *    （若按规范 §2 的兜底规则写成 `NORTH` 会凭空触发一次北面重扫）。
 *  - `getIcon` / `customTextures` / `registerBlockIcons` / `Textures.Adapter.iconOn` 全部删除，
 *    面纹理改由模型 JSON 指定（见下）。
 *  - 扳手集成（`integration.util.Wrench`）尚未移植，右键切换敞开面的分支保留但恒不触发。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = `AdapterTop`，北 / 南 / 西 / 东 = `AdapterSide`。
 * 原 `AdapterOn`（面接通时替换 `AdapterSide`）需要客户端按方块实体状态换模型，
 * TODO(客户端): `li.cil.oc.client` 移植后通过 `BlockEntityRenderer` 或模型覆写恢复。
 */
class Adapter(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) with traits.GUI {

  override def guiType = GuiType.Adapter

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Adapter(pos, state)

  // ----------------------------------------------------------------------- //
  // 邻居变化
  // ----------------------------------------------------------------------- //

  override def onNeighborBlockChange(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block): Unit =
    level.getBlockEntity(pos) match {
      case adapter: tileentity.Adapter => adapter.neighborChanged()
      case _ => // Ignore.
    }

  override def onNeighborChanged(state: BlockState, level: LevelReader, pos: BlockPos, neighborPos: BlockPos): Unit =
    level.getBlockEntity(pos) match {
      case adapter: tileentity.Adapter =>
        val dx = neighborPos.getX - pos.getX
        val dy = neighborPos.getY - pos.getY
        val dz = neighborPos.getZ - pos.getZ
        val index = 3 + dx + dy + dy + dz + dz + dz
        if (index >= 0 && index < sides.length) {
          // 原 1.7.10 直接传 `sides(index)`（可能是 `UNKNOWN`）；这里用 `Option` 表达同一语义。
          sides(index).foreach(adapter.neighborChanged)
        }
      case _ => // Ignore.
    }

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    // 原：`Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))`
    val holdsWrench = false // TODO(integration.util.Wrench): 扳手集成移植后恢复判断
    if (holdsWrench) {
      val sideToToggle = if (player.isShiftKeyDown) hit.getDirection.getOpposite else hit.getDirection
      level.getBlockEntity(pos) match {
        case adapter: tileentity.Adapter =>
          if (!level.isClientSide) {
            val oldValue = adapter.openSides(sideToToggle.ordinal())
            adapter.setSideOpen(sideToToggle, !oldValue)
          }
          InteractionResult.sidedSuccess(level.isClientSide)
        case _ => InteractionResult.PASS
      }
    }
    else super.useBlock(state, level, pos, player, hit)
  }

  /**
   * 邻居偏移 → 面 的查表（原 `sides`）。
   *
   * 下标由 `3 + dx + 2 * dy + 3 * dz` 得到，取值 0..6；其中下标 3 对应偏移 `(-1, 0, -1)`，
   * 不对应任何单个面（原为 `ForgeDirection.UNKNOWN`），因此为 `None`。
   */
  private val sides: Array[Option[Direction]] = Array(
    Some(Direction.NORTH),
    Some(Direction.DOWN),
    Some(Direction.WEST),
    None,
    Some(Direction.EAST),
    Some(Direction.UP),
    Some(Direction.SOUTH))
}
