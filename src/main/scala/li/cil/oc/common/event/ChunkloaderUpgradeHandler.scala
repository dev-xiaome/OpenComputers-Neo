package li.cil.oc.common.event

import java.util.UUID

import li.cil.oc.OpenComputersNeo
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.server.component.UpgradeChunkloader
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent
import net.neoforged.neoforge.common.world.chunk.TicketController

import scala.jdk.CollectionConverters._

/**
 * 区块加载器升级：把宿主周围的 3x3 区块强制加载。
 *
 * 1.21.1 迁移要点（与 1.7.10 的 `ForgeChunkManager` 差异较大）：
 *  - `ForgeChunkManager.requestTicket / releaseTicket / Ticket` 已整体移除，改为
 *    `TicketController` + `RegisterTicketControllersEvent`：控制器在 mod 事件总线上注册一次，
 *    之后用 `TicketController#forceChunk(level, owner, chunkX, chunkZ, add, ticking)`
 *    增删 ticket。ticket 由 `ForcedChunksSavedData` 自动存档，不再需要手工读写
 *    `Ticket#getModData` 里的 address/x/z，也不再需要「读档时认领 ticket」的逻辑。
 *  - ticket 的「所有者」可以是方块坐标（`BlockPos`）或实体 `UUID`；
 *    这里按宿主类型选择，并记录每个宿主上一次强制加载的区块集合，
 *    以便宿主移动或关闭时把不再需要的区块卸掉。
 *  - 原实现在 `LevelEvent.Save` 里回收「没人认领」的 ticket（`restoredTickets`）；
 *    新 API 下这属于 `LoadingValidationCallback` 的职责。本文件暂未提供该回调
 *    （回调在存档加载时执行，此时区块未加载、无法可靠判断宿主是否还在），
 *    因此**宿主方块被直接删除时留下的 ticket 不会自动清理**——见降级清单。
 */
object ChunkloaderUpgradeHandler {
  /**
   * ticket 的所有者：`Left` 为方块坐标，`Right` 为实体 UUID。
   * （1.21.1 的 `TicketController` 只有这两种所有者类型。）
   */
  private type Owner = Either[BlockPos, UUID]

  /** ticket 控制器 id；同一 id 的 ticket 会被一起校验 / 保存。 */
  private val controllerId = ResourceLocation.fromNamespaceAndPath(OpenComputersNeo.MODID, "chunkloader")

  val controller = new TicketController(controllerId)

  /** 每个宿主上一次强制加载的 `(所有者, 区块)` 集合，用于卸载不再需要的区块。 */
  private val loadedChunks = new java.util.WeakHashMap[UpgradeChunkloader, Set[(Owner, Long)]]()

  /**
   * 注册区块加载控制器与事件监听器。
   *
   * 控制器必须注册在 **mod 事件总线** 上（`RegisterTicketControllersEvent` 是 mod 总线事件），
   * 未注册的控制器其 ticket 会在读档时被丢弃，`forceChunk` 也会直接抛异常。
   */
  def initialize(modBus: IEventBus): Unit = {
    modBus.addListener((e: RegisterTicketControllersEvent) => e.register(controller))
    NeoForge.EVENT_BUS.addListener((e: RobotMoveEvent.Post) => onMove(e))
  }

  // 说明：机器人跨区块边界移动时可能需要先强制加载目标区块，否则移动会失败。
  // 2014-06-21 的测试表明并不需要：读取移动方向上的方块本身就会加载对应区块。

  def onMove(e: RobotMoveEvent.Post): Unit = {
    val machine = e.agent.machine
    // TODO(server.machine): 机器层移植前 `machine` 可能为 null，这里做空值保护。
    if (machine == null) return
    machine.node.reachableNodes.asScala.foreach(_.host match {
      case loader: UpgradeChunkloader => updateLoadedChunk(loader)
      case _ =>
    })
  }

  def updateLoadedChunk(loader: UpgradeChunkloader): Unit = loader.host.world() match {
    case level: ServerLevel =>
      val owner = ownerOf(loader.host)
      val center = BlockPos.containing(loader.host.xPosition(), loader.host.yPosition(), loader.host.zPosition())
      val centerChunk = new ChunkPos(center.getX >> 4, center.getZ >> 4)
      val robotChunks = (for (x <- -1 to 1; z <- -1 to 1) yield ChunkPos.asLong(centerChunk.x + x, centerChunk.z + z)).toSet

      val previous = Option(loadedChunks.get(loader)).getOrElse(Set.empty[(Owner, Long)])
      this.synchronized {
        // 卸掉离开 3x3 范围的区块（用记录下来的旧所有者，宿主移动后坐标可能已经变了）。
        for ((oldOwner, chunk) <- previous if !robotChunks.contains(chunk)) {
          force(level, oldOwner, chunk, add = false)
        }
        // 强制加载需要的区块。
        for (chunk <- robotChunks) {
          force(level, owner, chunk, add = true)
        }
        loadedChunks.put(loader, robotChunks.map(chunk => (owner, chunk)))
      }
    case _ => // 客户端 / 非服务端世界，什么也不做。
  }

  /** 宿主的所有者：实体用 UUID（会移动），其余用当前方块坐标。 */
  private def ownerOf(host: EnvironmentHost): Owner = host match {
    case entity: Entity => Right(entity.getUUID)
    case _ => Left(BlockPos.containing(host.xPosition(), host.yPosition(), host.zPosition()))
  }

  private def force(level: ServerLevel, owner: Owner, chunk: Long, add: Boolean): Boolean = owner match {
    case Left(pos) => controller.forceChunk(level, pos, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, false)
    case Right(uuid) => controller.forceChunk(level, uuid, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, false)
  }

  /**
   * 释放某个宿主强制加载的所有区块。
   *
   * 原实现在升级断开连接时释放 ticket；新 API 下由 [[li.cil.oc.server.component.UpgradeChunkloader]]
   * 在 `onDisconnect` / 电力耗尽时调用本方法。
   */
  def releaseLoadedChunks(loader: UpgradeChunkloader): Unit = loader.host.world() match {
    case level: ServerLevel => this.synchronized {
      Option(loadedChunks.remove(loader)).foreach(_.foreach {
        case (owner, chunk) => force(level, owner, chunk, add = false)
      })
    }
    case _ =>
  }
}
