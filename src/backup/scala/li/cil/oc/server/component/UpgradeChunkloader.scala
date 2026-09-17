package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.{ServerLevel, TicketType}
import net.minecraft.world.level.{ChunkPos, Level}

import scala.jdk.CollectionConverters._

/**
 * 区块加载升级：把宿主所在区块为中心的 3x3 区块强制加载。
 *
 * ==1.21.1 迁移要点==
 *  - `ForgeChunkManager` / `Ticket` / `ChunkCoordIntPair` 已整体移除。等价物是
 *    `ServerChunkCache#addRegionTicket / removeRegionTicket` + [[TicketType]]。
 *  - 不复用 `TicketType.FORCED`：那是 `/forceload` 与 NeoForge 的 `TicketController`
 *    共用的类型，多个升级加载同一区块时，任意一个释放都会把别人还在用的区块卸掉。
 *    这里使用本升级专属的 `TicketType`，使每个升级的票据互不干扰（与旧版
 *    “每个升级一张 `ForgeChunkManager.Ticket`” 的语义一致）。
 *  - `World#getTotalWorldTime` → `Level#getGameTime`。
 *  - 旧版公开维度白/黑名单用的是数字维度 ID；1.21.1 改用 `ResourceLocation`，
 *    这里只保留三个原版维度的旧数字映射（见 [[dimensionId]]）。
 *  - 旧版通过 `ChunkloaderUpgradeHandler` 的 `ticketsLoaded` 回调在重启后重新认领票据；
 *    新 API 下票据由 `ServerChunkCache` 自行维护，因此改为把“是否处于活动状态”
 *    存进 NBT，在 `onConnect` 时按需重新申请。
 *
 * ==已知降级==
 * TODO(server): `li.cil.oc.common.event.ChunkloaderUpgradeHandler`（`li.cil.oc.common.event` 包尚未
 * 进入编译范围）本应负责监听 `RobotMoveEvent` 并在机器人移动时刷新加载区块。这里把这部分
 * 逻辑内联到 [[update]]（宿主是实体时按 `tickFrequency` 刷新，实体宿主本来就走这条路；
 * 方块宿主则由同一个轮询覆盖，比旧版更及时）。等 `li.cil.oc.common.event` 包移植完成并加入编译范围后，
 * 建议只保留其中一处实现，避免两套票据系统同时生效。
 */
class UpgradeChunkloader(val host: EnvironmentHost) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("chunkloader").
    withConnector().
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Level stabilizer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Realizer9001-CL"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  /** 当前被强制加载的区块集合；非空即表示「活动」。 */
  private var loadedChunks: Set[ChunkPos] = Set.empty

  /** 读档时记录的「之前是活动的」，用于 `onConnect` 后重新申请票据。 */
  private var restoreActive = false

  override val canUpdate = true

  /** 是否处于活动状态（已申请到票据）。 */
  private def active: Boolean = loadedChunks.nonEmpty

  override def update(): Unit = {
    super.update()
    if (host.world.getGameTime % Settings.get.tickFrequency == 0 && active) {
      if (!node.tryChangeBuffer(-Settings.get.chunkloaderCost * Settings.get.tickFrequency)) {
        releaseTicket()
      }
      else updateLoadedChunk()
    }
  }

  @Callback(doc = "function():boolean -- Gets whether the chunkloader is currently active.")
  def isActive(context: Context, args: Arguments): Array[AnyRef] = result(active)

  @Callback(doc = "function(enabled:boolean):boolean -- Enables or disables the chunkloader, returns true if active changed")
  def setActive(context: Context, args: Arguments): Array[AnyRef] = result(setActive(args.checkBoolean(0), throwIfBlocked = true))

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      if (!isDimensionAllowed) {
        OpenComputers.log.info(s"Not chunk loading at (${host.xPosition()}, ${host.yPosition()}, ${host.zPosition()}) in blacklisted dimension ${host.world().dimension().location()}.")
      } else if (restoreActive) {
        OpenComputers.log.info(s"Reclaiming chunk loader at (${host.xPosition()}, ${host.yPosition()}, ${host.zPosition()}) in dimension ${host.world().dimension().location()}.")
        restoreActive = false
        requestTicket()
      } else host match {
        case context: Context if context.isRunning => requestTicket()
        case _ =>
      }
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      releaseTicket()
    }
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "computer.stopped") {
      setActive(enabled = false)
    }
    else if (message.name == "computer.started") {
      setActive(enabled = true)
    }
  }

  private def setActive(enabled: Boolean, throwIfBlocked: Boolean = false): Boolean = {
    if (enabled && !active) {
      requestTicket(throwIfBlocked)
      active
    }
    else if (!enabled && active) {
      releaseTicket()
      true
    } else {
      false
    }
  }

  /**
   * 当前维度的旧式数字 ID。
   *
   * 1.21.1 已经没有数字维度 ID，只有 `ResourceLocation`；为了让旧的
   * `chunkloader.dimWhitelist / dimBlacklist` 配置仍然可用，这里只映射三个原版维度
   * （`0` / `-1` / `1`，与原版数字 ID 一致）。模组维度没有对应数字，返回 `None`，
   * 于是在白名单非空时视为「不在白名单内」。
   */
  private def dimensionId: Option[Int] = host.world().dimension() match {
    case Level.OVERWORLD => Some(0)
    case Level.NETHER => Some(-1)
    case Level.END => Some(1)
    case _ => None
  }

  private def isDimensionAllowed: Boolean = {
    val whitelist = Settings.get.chunkloadDimensionWhitelist
    val blacklist = Settings.get.chunkloadDimensionBlacklist
    if (!whitelist.isEmpty) {
      if (!dimensionId.exists(id => whitelist.contains(id))) {
        return false
      }
    }
    if (!blacklist.isEmpty) {
      if (dimensionId.exists(id => blacklist.contains(id))) {
        return false
      }
    }
    true
  }

  private def requestTicket(throwIfBlocked: Boolean = false): Unit = {
    if (!isDimensionAllowed) {
      if (throwIfBlocked) {
        throw new Exception("this dimension is blacklisted")
      }
    } else {
      updateLoadedChunk()
    }
  }

  /** 释放全部票据，并把加载区块清空。 */
  private def releaseTicket(): Unit = {
    host.world() match {
      case level: ServerLevel => releaseChunks(level, loadedChunks)
      case _ => // 客户端 / 非服务端世界没有票据可释放。
    }
    loadedChunks = Set.empty
  }

  /** 让已加载的区块集合与宿主当前所在的 3x3 区块保持一致。 */
  private def updateLoadedChunk(): Unit = host.world() match {
    case level: ServerLevel =>
      val center = new ChunkPos(math.floor(host.xPosition()).toInt >> 4, math.floor(host.zPosition()).toInt >> 4)
      val desired = (for (x <- -1 to 1; z <- -1 to 1) yield new ChunkPos(center.x + x, center.z + z)).toSet
      if (desired != loadedChunks) {
        releaseChunks(level, loadedChunks -- desired)
        val source = level.getChunkSource
        for (chunk <- desired -- loadedChunks) {
          source.addRegionTicket(UpgradeChunkloader.ticketType, chunk, UpgradeChunkloader.ticketLevel, this)
        }
        loadedChunks = desired
      }
    case _ => // 客户端 / 非服务端世界没有票据可申请。
  }

  private def releaseChunks(level: ServerLevel, chunks: Iterable[ChunkPos]): Unit = {
    val source = level.getChunkSource
    for (chunk <- chunks) {
      source.removeRegionTicket(UpgradeChunkloader.ticketType, chunk, UpgradeChunkloader.ticketLevel, this)
    }
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    restoreActive = nbt.getBoolean("active")
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.putBoolean("active", active)
  }
}

object UpgradeChunkloader {
  /**
   * 票据的加载等级：与 `ServerLevel#setChunkForced`（即 `/forceload`）使用的值保持一致，
   * 这样被加载的区块会正常参与方块与实体 tick。
   */
  private final val ticketLevel = 1

  /**
   * 本升级专属的票据类型。
   *
   * `TicketType.create` 每次都会新建一个类型，`TicketType` 之间按身份比较，
   * 因此不会与 `/forceload`（`TicketType.FORCED`）或其它模组的票据互相干扰。
   */
  private val ticketType: TicketType[UpgradeChunkloader] =
    TicketType.create("opencomputers_neo:chunkloader", new java.util.Comparator[UpgradeChunkloader] {
      override def compare(a: UpgradeChunkloader, b: UpgradeChunkloader): Int =
        Integer.compare(System.identityHashCode(a), System.identityHashCode(b))
    })
}
