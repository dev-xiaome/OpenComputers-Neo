package li.cil.oc.server.agent

import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import li.cil.oc.OpenComputers
import li.cil.oc.api.network.Node
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.neoforged.bus.api.{EventPriority, SubscribeEvent}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.{BlockDropsEvent, BlockEvent}

import scala.collection.convert.ImplicitConversionsToScala._

object PlayerInteractionManagerHelper {
  private def isDestroyingBlock(player: Player): Boolean = {
    player.gameMode.isDestroyingBlock
  }

  def onBlockClicked(player: Player, pos: BlockPos, side: Direction): Boolean = {
    val buildLimit = player.level.getMaxBuildHeight();
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

  // returns exp gained from removing the block, -1 if block not removed
  // redone here because the interaction manager just drops the xp on the ground
  def blockRemoving(player: Player, pos: BlockPos): Int = {
    if (!isDestroyingBlock(player)) {
      return -1
    }

    //PlayerEvent.BreakSpeed
    class BreakHandler(val player: Player) {
      var expToDrop: Int = 0

      val hasExperienceUpgrade: Boolean = {
        val machineNode = player.agent.machine.node
        machineNode.reachableNodes.exists {
          case node: Node if node.canBeReachedFrom(machineNode) =>
            node.host.isInstanceOf[li.cil.oc.common.item.UpgradeExperience] ||
            node.host.isInstanceOf[li.cil.oc.server.component.UpgradeExperience]
          case _ => false
        }
      }

      @SubscribeEvent(priority = EventPriority.LOWEST)
      def onBreakSpeedEvent(breakSpeedEvent: PlayerEvent.BreakSpeed): Unit = {
        if (player == breakSpeedEvent.getEntity)
          breakSpeedEvent.setNewSpeed(scala.Float.MaxValue)
      }

      @SubscribeEvent(priority = EventPriority.LOWEST)
      def onExperienceBreakEvent(blockDropsEvent: BlockDropsEvent): Unit = {
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
    val buildLimit = player.level.getMaxBuildHeight;
    try {
      player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, null, buildLimit, 0)
      infBreaker.expToDrop
    } catch {
      case e: Exception => {
        OpenComputers.log.info(s"an exception was thrown while trying to call handleBlockBreakAction: ${e.getMessage}")
        player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, null, buildLimit, 0)
        -1
      }
    } finally {
      NeoForge.EVENT_BUS.unregister(infBreaker)
    }
  }
}
