package li.cil.oc.common.event

import java.util.UUID
import li.cil.oc.OpenComputers
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.server.component.UpgradeChunkloader
import li.cil.oc.util.BlockPosition
import net.neoforged.bus.api.{IEventBus, SubscribeEvent}
import net.neoforged.neoforge.common.world.chunk.{LoadingValidationCallback, RegisterTicketControllersEvent, TicketController, TicketHelper}

import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.immutable
import scala.collection.mutable
import net.minecraft.server.level.ServerLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.neoforged.neoforge.event.level.LevelEvent

/**
 * 区块加载器升级：把宿主周围的 3x3 区块保持强制加载。
 *
 * 1.21.1 迁移要点（`ForgeChunkManager` 整套搬家并改型）：
 *  - Forge 1.20 的 `net.neoforged.neoforge.common.world.ForgeChunkManager` 在 NeoForge 中
 *    不存在，取代它的是 `net.neoforged.neoforge.common.world.chunk.TicketController`：
 *    控制器先在 **mod 事件总线** 的 `RegisterTicketControllersEvent` 里注册一次，
 *    之后所有增删都走 `TicketController#forceChunk(level, owner, chunkX, chunkZ, add, ticking)`。
 *    `TicketHelper` / `LoadingValidationCallback` 也跟着搬到了同一个 `...common.world.chunk` 包。
 *  - `forceChunk` 的所有者只有两种：`BlockPos`（方块）或 `UUID`（实体）。本 mod 用的是
 *    节点地址（一个 UUID 字符串），因此统一按 `UUID` 所有者处理，语义与旧 ticket 的
 *    `owner` 一致。
 *  - ticket 的存档 / 读档由 `ForcedChunksSavedData` + 控制器自动完成，不再需要像旧版那样
 *    手工往 `Ticket#getModData` 里塞 address / x / z。
 *  - `claimTicket` 的语义（读档时把上一次的 3x3 ticket 认领回某个节点地址）保留：
 *    `validateTickets` 里先把所有实体 ticket 标记为「孤儿」，能凑成 3x3 形状的记下中心区块，
 *    之后由 [[li.cil.oc.server.component.UpgradeChunkloader]] 通过节点地址认领。
 */
object ChunkloaderUpgradeHandler extends LoadingValidationCallback {
  private val restoredTickets = mutable.Map.empty[UUID, ChunkPos]

  /**
   * ticket 控制器。构造时挂上本对象的 `validateTickets` 回调，读档校验就由 NeoForge 调用它。
   *
   * 注意必须通过 [[initialize]] 注册到 mod 事件总线上，否则控制器未注册：
   * `forceChunk` 会抛异常，已有的 ticket 也会在读档时被当作非法而丢弃。
   */
  val controller: TicketController = new TicketController(
    ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "chunkloader"), this)

  /**
   * 注册 ticket 控制器。
   *
   * `RegisterTicketControllersEvent` 是 mod 总线事件（`IModBusEvent`），因此必须在
   * mod 事件总线上注册，不能挂在 `NeoForge.EVENT_BUS` 上。
   */
  def initialize(modBus: IEventBus): Unit = {
    modBus.addListener((e: RegisterTicketControllersEvent) => e.register(controller))
  }

  private def parseAddress(addr: String): Option[UUID] = try {
    Some(UUID.fromString(addr))
  }
  catch {
    case _: RuntimeException => None
  }

  def claimTicket(addr: String) = parseAddress(addr).flatMap(restoredTickets.remove)

  override def validateTickets(world: ServerLevel, helper: TicketHelper): Unit = {
    for ((owner, ticketsPair) <- helper.getEntityTickets) {
      // This ensures that malformed tickets are also cleared on world save.
      restoredTickets += owner -> null
      // Chunkloaders use only ticking tickets.
      // 1.21.1：旧版的 `Pair<Set<Long>, Set<Long>>` 换成了 `TicketSet` 记录，
      // 取「强制加载（ticking）」那一半用 `ticking()`。
      val tickets = ticketsPair.ticking()
      if (tickets.size == 9) {
        var (minX, minZ, maxX, maxZ) = (0, 0, 0, 0)
        for (combinedPos <- tickets) {
          val x = ChunkPos.getX(combinedPos)
          val z = ChunkPos.getZ(combinedPos)
          minX = minX min x
          maxX = maxX max x
          minZ = minZ min z
          maxZ = maxZ max z
        }
        if (minX + 2 == maxX && minZ + 2 == maxZ) {
          val x = minX + 1
          val z = minZ + 1
          OpenComputers.log.info(s"Restoring chunk loader ticket for upgrade at chunk ($x, $z) with address ${owner}.")
          restoredTickets += owner -> new ChunkPos(x, z)
        }
        else {
          OpenComputers.log.warn(s"Chunk loader ticket for $owner loads an incorrect shape.")
          helper.removeAllTickets(owner)
        }
      }
      else {
        OpenComputers.log.warn(s"Chunk loader ticket for $owner loads ${tickets.size} chunks.")
        helper.removeAllTickets(owner)
      }
    }
  }

  @SubscribeEvent
  def onWorldSave(e: LevelEvent.Save) = e.getLevel match {
    case level: ServerLevel => {
      // Any tickets that were not reassigned by the time the level gets saved
      // again can be considered orphaned, so we release them.
      // TODO figure out a better event *after* tile entities were restored
      // but *before* the level is saved, because the tickets are saved first,
      // so if the save is because the game is being quit the tickets aren't
      // actually being cleared. This will *usually* not be a problem, but it
      // has room for improvement.
      for ((owner, pos) <- restoredTickets) {
        try {
          OpenComputers.log.warn(s"A chunk loader ticket has been orphaned! Address: ${owner}, position: (${pos.x}, ${pos.z}). Removing...")
          releaseTicket(level, owner.toString, pos)
        }
        catch {
          case err: Throwable => OpenComputers.log.error(err)
        }
      }
      restoredTickets.clear()
    }
    case _ =>
  }

  // Note: it might be necessary to use pre move to force load the target chunk
  // in case the robot moves across a chunk border into an otherwise unloaded
  // chunk (I think it would just fail to move otherwise).
  // Update 2014-06-21: did some testing, seems not to be necessary. My guess
  // is that the access to the block in the direction the robot moves causes
  // the chunk it might move into to get loaded.

  @SubscribeEvent
  def onMove(e: RobotMoveEvent.Post): Unit = {
    val machineNode = e.agent.machine.node
    machineNode.reachableNodes.foreach(_.host match {
      case loader: UpgradeChunkloader => updateLoadedChunk(loader)
      case _ =>
    })
  }

  def releaseTicket(level: ServerLevel, addr: String, pos: ChunkPos): Unit = parseAddress(addr) match {
    case Some(uuid) => {
      for (x <- -1 to 1; z <- -1 to 1) {
        controller.forceChunk(level, uuid, pos.x + x, pos.z + z, false, true)
      }
    }
    case _ => OpenComputers.log.warn("Address '$addr' could not be parsed")
  }

  def updateLoadedChunk(loader: UpgradeChunkloader): Unit = {
    (loader.host.getEnvironmentLevel, parseAddress(loader.node.address)) match {
      // If loader.ticket is None that means we shouldn't load anything (as did the old ticketing system).
      case (level: ServerLevel, Some(owner)) if loader.ticket.isDefined => {
        val blockPos = BlockPosition(loader.host)
        val centerChunk = new ChunkPos(blockPos.x >> 4, blockPos.z >> 4)
        if (centerChunk != loader.ticket.get) {
          val robotChunks = (for (x <- -1 to 1; z <- -1 to 1) yield new ChunkPos(centerChunk.x + x, centerChunk.z + z)).toSet
          val existingChunks = loader.ticket match {
            case Some(currPos) => (for (x <- -1 to 1; z <- -1 to 1) yield new ChunkPos(currPos.x + x, currPos.z + z)).toSet
            case None => immutable.Set.empty[ChunkPos]
          }
          for (toRemove <- existingChunks if !robotChunks.contains(toRemove)) {
            controller.forceChunk(level, owner, toRemove.x, toRemove.z, false, true)
          }
          for (toAdd <- robotChunks if !existingChunks.contains(toAdd)) {
            controller.forceChunk(level, owner, toAdd.x, toAdd.z, true, true)
          }
          loader.ticket = Some(centerChunk)
        }
      }
      case _ =>
    }
  }
}
