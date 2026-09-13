package li.cil.oc.common.nanomachines.provider

import net.neoforged.bus.api.Event
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.ForgeEventFactory
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action

import scala.collection.mutable

object DisintegrationProvider extends ScalaProvider("c4e7e3c2-8069-4fbb-b08e-74b1bddcdfe7") {
  override def createScalaBehaviors(player: Player) = Iterable(new DisintegrationBehavior(player))

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag) = new DisintegrationBehavior(player)

  class DisintegrationBehavior(player: Player) extends AbstractBehavior(player) {
    var breakingMap = mutable.Map.empty[BlockPosition, SlowBreakInfo]
    var breakingMapNew = mutable.Map.empty[BlockPosition, SlowBreakInfo]

    // Note: intentionally not overriding getNameHint. Gotta find this one manually!

    override def onDisable(reason: DisableReason): Unit = {
      val world = player.getEntityWorld
      for (pos <- breakingMap.keys) {
        world.destroyBlockInWorldPartially(pos.hashCode(), pos, -1)
      }
      breakingMap.clear()
    }

    override def update(): Unit = {
      val world = player.getEntityWorld
      if (!world.isRemote) player match {
        case _: FakePlayer => // Nope
        case playerMP: ServerPlayer =>
          val now = world.getTotalWorldTime

          // Check blocks in range.
          val blockPos = BlockPosition(player)
          val actualRange = Settings.get.nanomachineDisintegrationRange * api.Nanomachines.getController(player).getInputCount(this)
          for (x <- -actualRange to actualRange; y <- 0 to actualRange * 2; z <- -actualRange to actualRange) {
            val pos = BlockPosition(blockPos.offset(x, y, z))
            breakingMap.get(pos) match {
              case Some(info) if info.checkTool(player) =>
                breakingMapNew += pos -> info
                info.update(world, player, now)
              case None =>
                val event = ForgeEventFactory.onPlayerInteract(player, Action.LEFT_CLICK_BLOCK, pos.x, pos.y, pos.z, 0, world)
                val allowed = !event.isCanceled && event.useBlock != Event.Result.DENY && event.useItem != Event.Result.DENY
                val adventureOk = !world.getWorldInfo.getGameType.isAdventure || player.isCurrentToolAdventureModeExempt(pos.x, pos.y, pos.z)
                if (allowed && adventureOk && !world.isAirBlock(pos)) {
                  val block = world.getBlock(pos)
                  val hardness = block.getPlayerRelativeBlockHardness(player, world, pos.x, pos.y, pos.z)
                  if (hardness > 0) {
                    val timeToBreak = (1 / hardness).toInt
                    if (timeToBreak < 20 * 30) {
                      val meta = world.getBlockMetadata(pos)
                      val info = new SlowBreakInfo(now, now + timeToBreak, pos, Option(player.getHeldItem).map(_.copy()), block, meta)
                      world.destroyBlockInWorldPartially(pos.hashCode(), pos, 0)
                      breakingMapNew += pos -> info
                    }
                  }
                }
              case _ => // Tool changed, pretend block doesn't exist for this tick.
            }
          }

          // Handle completed breaks.
          for ((pos, info) <- breakingMap) {
            if (info.timeBroken < now) {
              breakingMapNew -= pos
              info.finish(world, playerMP)
            }
          }

          // Handle aborted / incomplete breaks.
          for (pos <- breakingMap.keySet -- breakingMapNew.keySet) {
            world.destroyBlockInWorldPartially(pos.hashCode(), pos, -1)
          }

          val tmp = breakingMap
          breakingMap.clear()
          breakingMap = breakingMapNew
          breakingMapNew = tmp
        case _ => // Not available for fake players, sorry :P
      }
    }
  }

  class SlowBreakInfo(val timeStarted: Long, val timeBroken: Long, val pos: BlockPosition, val originalTool: Option[ItemStack], val block: Block, val meta: Int) {
    var lastDamageSent = 0

    def checkTool(player: Player): Boolean = {
      val currentTool = Option(player.getHeldItem).map(_.copy())
      (currentTool, originalTool) match {
        case (Some(stackA), Some(stackB)) => stackA.getItem == stackB.getItem && (stackA.isItemStackDamageable || stackA.getItemDamage == stackB.getItemDamage)
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
          world.destroyBlockInWorldPartially(pos.hashCode(), pos, lastDamageSent)
        }
      }
    }

    def finish(world: Level, player: ServerPlayer): Unit = {
      val sameBlock = world.getBlock(pos) == block && world.getBlockMetadata(pos) == meta
      if (sameBlock) {
        world.destroyBlockInWorldPartially(pos.hashCode(), pos, -1)
        if (player.theItemInWorldManager.tryHarvestBlock(pos.x, pos.y, pos.z)) {
          world.playAuxSFX(2001, pos, Block.getIdFromBlock(block) + (meta << 12))
        }
      }
    }
  }

}
