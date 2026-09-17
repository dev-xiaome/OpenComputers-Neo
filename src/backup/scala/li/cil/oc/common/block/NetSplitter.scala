package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.BlockHitResult

/**
 * 网络分线器（原 1.7.10 `NetSplitter`，按面开关网络连接）。
 *
 * 1.21.1 迁移要点：
 *  - 继承 [[RedstoneAware]]（方块实体同时是 `api.network.SidedEnvironment` 与
 *    `traits.OpenSides` 的宿主，见 `tileentity.NetSplitter`）；
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]：原实现先用扳手判断，
 *    非扳手时交给默认交互；
 *    TODO(integration.util.Wrench): `integration.util.Wrench` 未移植，判断恒为 `false`，
 *    因此「扳手切换某面开/关」的分支目前不可达，等扳手集成移植后恢复
 *    `Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))`；
 *  - `isSideSolid`（原恒 `false`）在 1.21.1 由「碰撞形状 + 面坚固判定」取代，不再覆写；
 *  - 原客户端侧 `Textures.NetSplitter.iconOn`（开启时换贴图）删除，
 *    TODO(客户端): `li.cil.oc.client` 移植后用状态模型或 `BlockEntityRenderer` 恢复。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = `NetSplitterTop`，北 / 南 / 西 / 东 = `NetSplitterSide`；
 * 开启时原为 `NetSplitterOn`。
 *
 * 注：1.21.1 的 `Direction#ordinal` 与原 `ForgeDirection` 完全同序
 * （DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5），因此
 * `splitter.openSides(side.ordinal())` 的语义不变。
 */
class NetSplitter(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) {

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.NetSplitter(pos, state)

  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    // 原：`Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))`
    val holdsWrench = false // TODO(integration.util.Wrench): 扳手集成移植后恢复判断
    if (holdsWrench) {
      val sideToToggle =
        if (player.isShiftKeyDown) hit.getDirection.getOpposite
        else hit.getDirection
      level.getBlockEntity(pos) match {
        case splitter: tileentity.NetSplitter =>
          if (!level.isClientSide) {
            val oldValue = splitter.openSides(sideToToggle.ordinal())
            splitter.setSideOpen(sideToToggle, !oldValue)
          }
          InteractionResult.sidedSuccess(level.isClientSide)
        case _ => InteractionResult.PASS
      }
    }
    else super.useBlock(state, level, pos, player, hit)
  }
}
