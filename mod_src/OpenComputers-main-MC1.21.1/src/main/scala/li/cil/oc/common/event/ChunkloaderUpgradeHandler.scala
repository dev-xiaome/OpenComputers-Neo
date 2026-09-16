package li.cil.oc.common.event

import li.cil.oc.OpenComputers
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.server.component.UpgradeChunkloader
import li.cil.oc.util.BlockPosition
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.{ChunkPos, Level}
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.world.chunk.{
  RegisterTicketControllersEvent,
  TicketController,
  TicketHelper
}
import net.neoforged.neoforge.event.level.LevelEvent

import java.util.UUID
import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.{immutable, mutable}

object ChunkloaderUpgradeHandler {
  private case class RestoredTicket(dimension: ResourceKey[Level], pos: ChunkPos)
  private val restoredTickets = mutable.Map.empty[UUID, RestoredTicket]
  var ticketController: TicketController = _

  private def parseAddress(addr: String): Option[UUID] = try {
    Some(UUID.fromString(addr))
  } catch {
    case _: RuntimeException => None
  }

  def claimTicket(addr: String, level: Level): Option[ChunkPos] = parseAddress(addr).flatMap { owner =>
    restoredTickets.get(owner) match {
      case Some(ticket) if ticket.dimension == level.dimension =>
        restoredTickets.remove(owner)
        Some(ticket.pos)
      case _ => None
    }
  }

  def onRegisterTicketControllers(event: RegisterTicketControllersEvent): Unit = {
    ticketController = new TicketController(
      ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "chunkloader"),
      (world, helper) => validateTickets(world, helper)
    )
    event.register(ticketController)
  }

  private def validateTickets(world: ServerLevel, helper: TicketHelper): Unit = {
    for ((owner, ticketSet) <- helper.getEntityTickets) {
      val tickets = ticketSet.ticking()
      if (tickets.size == 9) {
        val positions = tickets.map(combinedPos => new ChunkPos(ChunkPos.getX(combinedPos), ChunkPos.getZ(combinedPos))).toSeq
        val minX = positions.map(_.x).min
        val maxX = positions.map(_.x).max
        val minZ = positions.map(_.z).min
        val maxZ = positions.map(_.z).max
        val expected = (for (x <- minX to maxX; z <- minZ to maxZ) yield new ChunkPos(x, z)).toSet

        if (maxX - minX == 2 && maxZ - minZ == 2 && positions.toSet == expected) {
          val x = minX + 1
          val z = minZ + 1
          OpenComputers.log.info(s"Restoring chunk loader ticket for upgrade at chunk ($x, $z) with address ${owner}.")
          restoredTickets += owner -> RestoredTicket(world.dimension, new ChunkPos(x, z))
        } else {
          OpenComputers.log.warn(s"Chunk loader ticket for $owner loads an incorrect shape.")
          helper.removeAllTickets(owner)
          restoredTickets.remove(owner)
        }
      } else {
        OpenComputers.log.warn(s"Chunk loader ticket for $owner loads ${tickets.size} chunks.")
        helper.removeAllTickets(owner)
        restoredTickets.remove(owner)
      }
    }
  }

  @SubscribeEvent
  def onWorldSave(e: LevelEvent.Save): Unit = e.getLevel match {
    case level: ServerLevel =>
      val orphaned = restoredTickets.collect {
        case (owner, ticket) if ticket.dimension == level.dimension => owner -> ticket
      }.toSeq
      for ((owner, ticket) <- orphaned) {
        try {
          OpenComputers.log.warn(s"A chunk loader ticket has been orphaned! Address: ${owner}, position: (${ticket.pos.x}, ${ticket.pos.z}) in ${ticket.dimension.location}. Removing...")
          releaseTicket(level, owner.toString, ticket.pos)
        } catch {
          case err: Throwable => OpenComputers.log.error(err)
        } finally {
          restoredTickets.remove(owner)
        }
      }
    case _ =>
  }

  @SubscribeEvent
  def onMove(e: RobotMoveEvent.Post): Unit = {
    val machineNode = e.agent.machine.node
    machineNode.reachableNodes.foreach(_.host match {
      case loader: UpgradeChunkloader => updateLoadedChunk(loader)
      case _ =>
    })
  }

  def releaseTicket(level: ServerLevel, addr: String, pos: ChunkPos): Unit = parseAddress(addr) match {
    case Some(uuid) =>
      for (x <- -1 to 1; z <- -1 to 1) {
        ticketController.forceChunk(level, uuid, pos.x + x, pos.z + z, false, true)
      }
    case _ => OpenComputers.log.warn(s"Address '$addr' could not be parsed")
  }

  def requestTicket(loader: UpgradeChunkloader): Unit = {
    (loader.host.getEnvironmentLevel, parseAddress(loader.node.address)) match {
      case (level: ServerLevel, Some(owner)) if loader.ticket.isEmpty =>
        val blockPos = BlockPosition(loader.host)
        val centerChunk = new ChunkPos(blockPos.x >> 4, blockPos.z >> 4)
        for (x <- -1 to 1; z <- -1 to 1) {
          ticketController.forceChunk(level, owner, centerChunk.x + x, centerChunk.z + z, true, true)
        }
        loader.ticket = Some(centerChunk)
      case _ =>
    }
  }

  def updateLoadedChunk(loader: UpgradeChunkloader): Unit = {
    (loader.host.getEnvironmentLevel, parseAddress(loader.node.address)) match {
      case (level: ServerLevel, Some(owner)) if loader.ticket.isDefined =>
        val blockPos = BlockPosition(loader.host)
        val centerChunk = new ChunkPos(blockPos.x >> 4, blockPos.z >> 4)
        if (centerChunk != loader.ticket.get) {
          val robotChunks = (for (x <- -1 to 1; z <- -1 to 1) yield new ChunkPos(centerChunk.x + x, centerChunk.z + z)).toSet
          val existingChunks = loader.ticket match {
            case Some(currPos) => (for (x <- -1 to 1; z <- -1 to 1) yield new ChunkPos(currPos.x + x, currPos.z + z)).toSet
            case None => immutable.Set.empty[ChunkPos]
          }
          for (toRemove <- existingChunks if !robotChunks.contains(toRemove)) {
            ticketController.forceChunk(level, owner, toRemove.x, toRemove.z, false, true)
          }
          for (toAdd <- robotChunks if !existingChunks.contains(toAdd)) {
            ticketController.forceChunk(level, owner, toAdd.x, toAdd.z, true, true)
          }
          loader.ticket = Some(centerChunk)
        }
      case _ =>
    }
  }
}
