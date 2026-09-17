package li.cil.oc.server.agent

import li.cil.oc.OpenComputers
import li.cil.oc.api.network.Node
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.server.level.ServerPlayerGameMode
import net.neoforged.bus.api.{EventPriority, SubscribeEvent}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockDropsEvent

import scala.jdk.CollectionConverters._

/**
 * 机器人挖掘方块时对 `ServerPlayerGameMode` 的驱动层（对应 OCCE 的同名对象）。
 *
 * 1.7.10 的 `Player#clickBlock` 把整条挖掘流程（`block.onBlockClicked`、破坏进度、
 * `block.harvestBlock`、经验掉落）都手写了一遍；OCCE 改为复用原版玩家的挖掘状态机：
 * `handleBlockBreakAction(START)` 开始、`gameMode.tick()` 推进、`handleBlockBreakAction(STOP)`
 * 收尾，本对象就是这三步的封装。
 *
 * ==1.20 Forge 到 1.21.1 NeoForge 的 API 翻译==
 *  - 收集经验的事件从 `BlockEvent.BreakEvent`（`getExpToDrop` / `setExpToDrop`）换成
 *    `BlockDropsEvent`（`getDroppedExperience` / `setDroppedExperience`，作者用 `getBreaker` 判定）。
 *  - `MinecraftForge.EVENT_BUS` 换成 `NeoForge.EVENT_BUS`。
 *
 * ==`isDestroyingBlock` 的读取方式==
 *  1.21.1 的 `ServerPlayerGameMode#isDestroyingBlock` 是 **private 字段**，没有公开读取入口，
 *  因此这里用反射读取它。若读取失败（例如映射变化），退化为「乐观认为正在挖掘」：只影响
 *  `onBlockClicked` 的返回值与进度推进，不影响最终由 `handleBlockBreakAction(STOP)` 完成的破坏。
 */
object PlayerInteractionManagerHelper {
  private val destroyingField: Option[java.lang.reflect.Field] = try {
    val field = classOf[ServerPlayerGameMode].getDeclaredField("isDestroyingBlock")
    field.setAccessible(true)
    Some(field)
  }
  catch {
    case t: Throwable =>
      OpenComputers.log.warn("Failed to reflect ServerPlayerGameMode#isDestroyingBlock, using an optimistic mining state instead.", t)
      None
  }

  private def isDestroyingBlock(player: Player): Boolean = destroyingField match {
    case Some(field) =>
      try field.getBoolean(player.gameMode)
      catch {
        case _: Throwable => true
      }
    case None => true
  }

  def onBlockClicked(player: Player, pos: BlockPos, side: Direction): Boolean = {
    val buildLimit = player.level.getMaxBuildHeight
    if (isDestroyingBlock(player)) {
      player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, side, buildLimit, 0)
    }
    player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, buildLimit, 0)
    isDestroyingBlock(player)
  }

  def updateBlockRemoving(player: Player): Boolean = {
    if (!isDestroyingBlock(player))
      return false
    player.gameMode.tick()
    isDestroyingBlock(player)
  }

  // 返回破坏方块得到的经验值；方块没有被破坏时返回 -1。
  // 这里自己收集经验是因为原版交互管理器会把经验直接掉在地上。
  def blockRemoving(player: Player, pos: BlockPos): Int = {
    if (!isDestroyingBlock(player)) {
      return -1
    }

    // 监听 PlayerEvent.BreakSpeed（把速度拉满，让 STOP 立即完成）与 BlockDropsEvent（截留经验）。
    class BreakHandler(val player: Player) {
      var expToDrop: Int = 0

      val hasExperienceUpgrade: Boolean = {
        val machine = player.agent.machine
        if (machine == null) false
        else {
          val machineNode = machine.node
          machineNode != null && machineNode.reachableNodes.asScala.exists {
            case node: Node if node.canBeReachedFrom(machineNode) =>
              node.host.isInstanceOf[li.cil.oc.common.item.UpgradeExperience] ||
                node.host.isInstanceOf[li.cil.oc.server.component.UpgradeExperience]
            case _ => false
          }
        }
      }

      @SubscribeEvent(priority = EventPriority.LOWEST)
      def onBreakSpeedEvent(breakSpeedEvent: PlayerEvent.BreakSpeed): Unit = {
        if (player == breakSpeedEvent.getEntity)
          breakSpeedEvent.setNewSpeed(scala.Float.MaxValue)
      }

      @SubscribeEvent(priority = EventPriority.LOWEST)
      def onBlockDropsEvent(blockDropsEvent: BlockDropsEvent): Unit = {
        if (player == blockDropsEvent.getBreaker) {
          if (hasExperienceUpgrade) {
            expToDrop += blockDropsEvent.getDroppedExperience
            blockDropsEvent.setDroppedExperience(0)
          }
        }
      }
    }

    val infBreaker = new BreakHandler(player)
    NeoForge.EVENT_BUS.register(infBreaker)
    val buildLimit = player.level.getMaxBuildHeight
    try {
      player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, null, buildLimit, 0)
      infBreaker.expToDrop
    }
    catch {
      case e: Exception =>
        OpenComputers.log.info(s"an exception was thrown while trying to call handleBlockBreakAction: ${e.getMessage}")
        player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, null, buildLimit, 0)
        -1
    }
    finally {
      NeoForge.EVENT_BUS.unregister(infBreaker)
    }
  }
}
