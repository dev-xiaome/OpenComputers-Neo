package li.cil.oc.common.nanomachines.provider

import com.google.common.base.Strings
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

import scala.collection.mutable

/**
 * 「分解」行为：让纳米机器慢慢挖掉玩家附近的方块。
 *
 * 1.21.1 迁移要点（改动较大，方块破坏流程整体重构过）：
 *  - `player.getEntityWorld` → `player.level()`；`World#isRemote` → `Level#isClientSide`
 *  - `world.getTotalWorldTime` → `level.getGameTime`
 *  - `world.isAirBlock(pos)` → `level.getBlockState(pos).isAir`
 *  - `block.getPlayerRelativeBlockHardness(player, world, x, y, z)` →
 *    `state.getDestroyProgress(player, level, pos)`（语义一致：每 tick 破坏进度）
 *  - `net.minecraftforge.common.util.FakePlayer` → `net.neoforged.neoforge.common.util.FakePlayer`
 *  - 交互事件：`ForgeEventFactory.onPlayerInteract(player, Action.LEFT_CLICK_BLOCK, ...)` →
 *    直接构造并 post `PlayerInteractEvent.LeftClickBlock`；判定从 `Event.Result.DENY`
 *    改为 `TriState.FALSE`
 *  - `getWorldInfo.getGameType.isAdventure` → `level.getGameRules` 无关，
 *    改用 `ServerPlayerGameMode#getGameModeForPlayer`（见 [[canHarvest]]）
 *  - `player.theItemInWorldManager.tryHarvestBlock(x, y, z)` →
 *    `player.gameMode.destroyBlock(pos)`
 *  - `world.playAuxSFX(2001, pos, Block.getIdFromBlock(block) + (meta << 12))` →
 *    `world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state))`
 *    （1.21.1 用 `BlockState` 而非 block + metadata 组合 id）
 *  - 元数据（metadata）在 1.21.1 已并入 [[BlockState]]，因此用 `BlockState` 代替 `(block, meta)`。
 */
object DisintegrationProvider extends ScalaProvider("c4e7e3c2-8069-4fbb-b08e-74b1bddcdfe7") {
  override def createScalaBehaviors(player: Player) = Iterable(new DisintegrationBehavior(player))

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag) = new DisintegrationBehavior(player)

  class DisintegrationBehavior(player: Player) extends AbstractBehavior(player) {
    var breakingMap = mutable.Map.empty[BlockPosition, SlowBreakInfo]
    var breakingMapNew = mutable.Map.empty[BlockPosition, SlowBreakInfo]

    // 注意：故意不覆写 getNameHint，这个行为必须靠手动方式找到。

    override def onDisable(reason: DisableReason): Unit = {
      val world = player.level()
      for (pos <- breakingMap.keys) {
        world.destroyBlockProgress(player.getId, pos.toChunkCoordinates, -1)
      }
      breakingMap.clear()
    }

    override def update(): Unit = {
      val world = player.level()
      if (!world.isClientSide) player match {
        case _: FakePlayer => // 假玩家不支持。
        case playerMP: ServerPlayer =>
          val now = world.getGameTime

          // 检查范围内的方块。
          val blockPos = BlockPosition(player)
          val actualRange = Settings.get.nanomachineDisintegrationRange * api.Nanomachines.getController(player).getInputCount(this)
          for (x <- -actualRange to actualRange; y <- 0 to actualRange * 2; z <- -actualRange to actualRange) {
            val pos = BlockPosition(blockPos.x + x, blockPos.y + y, blockPos.z + z, world)
            breakingMap.get(pos) match {
              case Some(info) if info.checkTool(player) =>
                breakingMapNew += pos -> info
                info.update(world, player, now)
              case None =>
                val allowed = allowInteraction(player, world, pos)
                if (allowed && !world.getBlockState(pos).isAir) {
                  val state = world.getBlockState(pos)
                  val hardness = state.getDestroyProgress(player, world, pos.toChunkCoordinates)
                  if (hardness > 0) {
                    val timeToBreak = (1 / hardness).toInt
                    if (timeToBreak < 20 * 30) {
                      val info = new SlowBreakInfo(now, now + timeToBreak, pos, currentTool(player), state)
                      world.destroyBlockProgress(player.getId, pos.toChunkCoordinates, 0)
                      breakingMapNew += pos -> info
                    }
                  }
                }
              case _ => // 工具变了，这一 tick 视作该方块不存在。
            }
          }

          // 处理已经挖完的方块。
          for ((pos, info) <- breakingMap) {
            if (info.timeBroken < now) {
              breakingMapNew -= pos
              info.finish(world, playerMP)
            }
          }

          // 处理被中断 / 未完成的挖掘。
          for (pos <- breakingMap.keySet -- breakingMapNew.keySet) {
            world.destroyBlockProgress(player.getId, pos.toChunkCoordinates, -1)
          }

          val tmp = breakingMap
          breakingMap.clear()
          breakingMap = breakingMapNew
          breakingMapNew = tmp
        case _ => // 假玩家不支持。
      }
    }

    /** 当前手持物品的快照（`null` 表示空手）。 */
    private def currentTool(player: Player): Option[ItemStack] = {
      val stack = player.getMainHandItem
      if (stack == null || stack.isEmpty) None else Option(stack.copy())
    }

    /**
     * 是否允许对该方块动作。
     *
     * 1.21.1：直接 post [[PlayerInteractEvent.LeftClickBlock]]，
     * 并额外检查冒险模式下的可交互性（旧版的
     * `getWorldInfo.getGameType.isAdventure || isCurrentToolAdventureModeExempt`）。
     */
    private def allowInteraction(player: Player, world: Level, pos: BlockPosition): Boolean = {
      val event = new PlayerInteractEvent.LeftClickBlock(
        player, pos.toChunkCoordinates, net.minecraft.core.Direction.UP,
        PlayerInteractEvent.LeftClickBlock.Action.START)
      NeoForge.EVENT_BUS.post(event)
      val allowed = !event.isCanceled && event.getUseBlock != TriState.FALSE && event.getUseItem != TriState.FALSE
      if (!allowed) return false

      player match {
        case serverPlayer: ServerPlayer =>
          val adventure = serverPlayer.gameMode.getGameModeForPlayer == net.minecraft.world.level.GameType.ADVENTURE
          !adventure || serverPlayer.mayInteract(world, pos.toChunkCoordinates)
        case _ => true
      }
    }
  }

  class SlowBreakInfo(val timeStarted: Long, val timeBroken: Long, val pos: BlockPosition,
                      val originalTool: Option[ItemStack], val state: BlockState) {
    var lastDamageSent = 0

    def checkTool(player: Player): Boolean = {
      val currentTool = {
        val stack = player.getMainHandItem
        if (stack == null || stack.isEmpty) None else Option(stack.copy())
      }
      (currentTool, originalTool) match {
        case (Some(stackA), Some(stackB)) =>
          // 1.21.1：`isItemStackDamageable` / `getItemDamage` 已被「伤害值数据组件」取代，
          // 这里用 `isDamageableItem` 与剩余伤害比较（语义一致：同类工具且损耗相同才继续）。
          stackA.getItem == stackB.getItem && (
            !stackA.isDamageableItem ||
              stackA.getDamageValue == stackB.getDamageValue)
        case (None, None) => true
        case _ => false
      }
    }

    def update(world: Level, player: Player, now: Long): Unit = {
      val timeTotal = timeBroken - timeStarted
      if (timeTotal > 0) {
        val timeTaken = now - timeStarted
        val damage = 10 * timeTaken / timeTotal
        if (damage != lastDamageSent) {
          lastDamageSent = damage.toInt
          world.destroyBlockProgress(player.getId, pos.toChunkCoordinates, lastDamageSent)
        }
      }
    }

    def finish(world: Level, player: ServerPlayer): Unit = {
      val current = world.getBlockState(pos.toChunkCoordinates)
      // 旧版比较 (block, metadata)；1.21.1 直接比较 BlockState。
      if (current == state) {
        world.destroyBlockProgress(player.getId, pos.toChunkCoordinates, -1)
        if (player.gameMode.destroyBlock(pos.toChunkCoordinates)) {
          world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos.toChunkCoordinates, Block.getId(state))
        }
      }
    }
  }

}
