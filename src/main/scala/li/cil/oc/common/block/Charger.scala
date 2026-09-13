package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.BlockHitResult

/**
 * 充电机（原 1.7.10 `Charger`）。
 *
 * 1.21.1 迁移要点：
 *  - `canConnectRedstone` → [[SimpleBlockHooks.canConnectRedstoneTo]]，这里固定返回 `true`
 *    （充电机允许任意面接红石，用来反转充/放电）。
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]；扳手切换充/放电方向的分支保留，
 *    但 `integration.util.Wrench` 未移植，判断恒为 `false`。
 *  - `PacketSender.sendChargerState(charger)` → 网络层未移植，
 *    改用方块更新同步（`sendBlockUpdated`），方块实体自身的客户端同步标签里带上这些字段。
 *    TODO(server.PacketSender): 网络层移植后改回发送 `ChargerState` 包。
 *  - `getIcon` / `customTextures` / `Textures.Charger.*` 全部删除，面纹理改由模型 JSON 指定。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = 未指定，北 = `ChargerSide`，南 = `ChargerFront`，西 / 东 = `ChargerSide`。
 * 原状态贴图：`ChargerFrontOn`、`ChargerSideOn`。
 */
class Charger(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) with traits.PowerAcceptor with traits.StateAware with traits.GUI {

  override def energyThroughput = Settings.get.chargerRate

  override def guiType = GuiType.Charger

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Charger(pos, state)

  // ----------------------------------------------------------------------- //
  // 红石
  // ----------------------------------------------------------------------- //

  override def canConnectRedstoneTo(state: BlockState, level: BlockGetter, pos: BlockPos, side: Direction): Boolean = true

  // ----------------------------------------------------------------------- //
  // 交互 / 邻居变化
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    // 原：`Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))`
    val holdsWrench = false // TODO(integration.util.Wrench): 扳手集成移植后恢复判断
    if (holdsWrench) level.getBlockEntity(pos) match {
      case charger: tileentity.Charger =>
        if (!level.isClientSide) {
          charger.invertSignal = !charger.invertSignal
          charger.chargeSpeed = 1.0 - charger.chargeSpeed
          // 原：`PacketSender.sendChargerState(charger)`
          level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
          // TODO(integration.util.Wrench): 原为 `Wrench.wrenchUsed(player, BlockPosition(x, y, z))`
        }
        InteractionResult.sidedSuccess(level.isClientSide)
      case _ => InteractionResult.PASS
    }
    else super.useBlock(state, level, pos, player, hit)
  }

  override def onNeighborBlockChange(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block): Unit = {
    level.getBlockEntity(pos) match {
      case charger: tileentity.Charger => charger.onNeighborChanged()
      case _ =>
    }
    super.onNeighborBlockChange(state, level, pos, neighborBlock)
  }
}
