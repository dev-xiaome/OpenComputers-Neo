package li.cil.oc.common

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream

import li.cil.oc.OpenComputers
import li.cil.oc.common.network.OpenComputersNetwork
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.server.ServerLifecycleHooks
import org.apache.logging.log4j.LogManager

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.reflect.classTag

/*
 * ============================================================================
 *  网络包分发层（1.21.1 / NeoForge）
 * ============================================================================
 *
 * 1.7.10 的形态：
 *   - `li.cil.oc.common.PacketHandler` 是抽象基类，负责解压、构造 `PacketParser`，
 *     然后把包按 `PacketType` 硬编码分派给子类的 `onXxx(p)` 方法；
 *   - `li.cil.oc.common.client/server.PacketHandler` 是子类，各自实现 `dispatch`，
 *     并在 `dispatch` 里对每个 `PacketType` 直接调用本文件的 `onXxx(p)`。
 *
 * 1.21.1 的形态：
 *   - 这条链路完整保留：`onData` → `dispatch` → 每个包的处理方法，
 *     `PacketParser` 的 `readTileEntity` / `readItemStack` / `readNBT` 等帮助方法也全部保留，
 *     因此 server/client 包移植时它们的 `onXxx(p)` 方法几乎可以原样搬过来。
 *   - 区别只在于**分派方式**：`li.cil.oc.server.PacketHandler` / `li.cil.oc.client.PacketHandler`
 *     所在的包尚未移植，基类不能硬编码引用它们的方法，所以改成**注册表**：
 *
 *         PacketType -> (PacketParser, PacketContext) => Unit
 *
 *     server/client 包移植完成后，只需把各个处理方法注册进来即可，例如：
 *
 *         // li.cil.oc.server.PacketHandler（object）里
 *         PacketHandler.registerServer(PacketType.ComputerPower, (p, _) => onComputerPower(p))
 *         PacketHandler.registerServer(PacketType.KeyDown, (p, _) => onKeyDown(p))
 *         ...
 *         // li.cil.oc.client.PacketHandler（object）里
 *         PacketHandler.registerClient(PacketType.Sound, (p, _) => onSound(p))
 *
 *     注册时机：这两个 object 的初始化（`object` 首次访问或主类里显式调用其 `initialize`）。
 *     未注册的包类型只写日志、绝不崩溃（见 `dispatch`），所以移植过程中网络层可以先跑起来。
 *   - `setServerDispatcher` / `setClientDispatcher` 这种「整个一侧的处理器」已经由本文件内的
 *     `PacketHandler.ServerSide` / `PacketHandler.ClientSide` 提供（纯注册表驱动），
 *     移植时**不需要**再写子类，直接往注册表里塞 `PacketType` 处理器即可。
 *   - 网络入口：主类必须调用一次 `PacketHandler.initialize(modBus)`
 *     （它会把两侧入口接到 Java 网络层 `OpenComputersNetwork` 上并注册 payload）。
 *
 * Payload 载体见 `li.cil.oc.common.network.OpenComputersPayload`：
 * 只承载 `byte[]`，内容仍是「包类型首字节 + 可选 Deflater 压缩流」，
 * 与 `PacketBuilder` 的写出格式一一对应。
 * ============================================================================
 */

/**
 * 处理器上下文：把「包解析需要知道的运行时信息」从处理器签名里抽出来。
 *
 * @param player        收到/发出该包的玩家（服务端侧是 [ServerPlayer]，客户端侧是本机玩家）
 * @param isServerSide  该处理器属于哪一侧（用于选择注册表、日志）
 * @param dimensionResolver 维度 id → 世界的解析函数，由子类提供（服务端查已加载维度，客户端只匹配当前维度）
 */
final class PacketContext(val player: Player,
                          val isServerSide: Boolean,
                          private val dimensionResolver: ResourceLocation => Option[Level]) {
  /** 解析维度对应的世界；维度为空或不存在时返回 None。 */
  def world(dimension: ResourceLocation): Option[Level] = if (dimension == null) None else dimensionResolver(dimension)

  /** 日志用的一侧名字。 */
  def sideName: String = if (isServerSide) "server" else "client"
}

/**
 * 包解析器。
 *
 * 与 1.7.10 版相比：维度不再用数字 id 而是 `ResourceLocation`（见 [PacketBuilder.writeDimension]），
 * 其余读取方法（`readTileEntity` / `readEntity` / `readDirection` / `readItemStack` /
 * `readNBT` / `readMedium`）保持原样，方便 server/client 包的代码直接搬运。
 *
 * 注意：本类本身就是 `DataInputStream`，所以注册表里的处理器可以直接把它当流用。
 */
class PacketParser(stream: InputStream, val player: Player, val context: PacketContext) extends DataInputStream(stream) {
  /** 包类型（负载的第一个字节）。 */
  val packetType: PacketType.Value = PacketType(readByte())

  def getTileEntity[T: ClassTag](dimension: ResourceLocation, x: Int, y: Int, z: Int): Option[T] = {
    context.world(dimension) match {
      case Some(world) =>
        val pos = new BlockPos(x, y, z)
        if (world.isLoaded(pos)) {
          val t = world.getBlockEntity(pos)
          if (t != null && classTag[T].runtimeClass.isAssignableFrom(t.getClass)) {
            return Some(t.asInstanceOf[T])
          }
          // 机器人可能在包到达前移动过（方块实体已经消失）。原版在这里查询
          // `RobotAfterimage`，但 `common/block` 尚未移植，所以改成注册式回退：
          // 移植完 `RobotAfterimage` 后调用 `PacketHandler.registerBlockEntityFallback` 即可。
          PacketHandler.findBlockEntityFallback(world, pos) match {
            case Some(fallback) if classTag[T].runtimeClass.isAssignableFrom(fallback.getClass) =>
              return Some(fallback.asInstanceOf[T])
            case _ =>
          }
        }
      case _ => // 无效维度。
    }
    None
  }

  def getEntity[T: ClassTag](dimension: ResourceLocation, id: Int): Option[T] = {
    context.world(dimension) match {
      case Some(world) =>
        val e = world.getEntity(id)
        if (e != null && classTag[T].runtimeClass.isAssignableFrom(e.getClass)) {
          return Some(e.asInstanceOf[T])
        }
      case _ =>
    }
    None
  }

  def readDimension(): ResourceLocation = {
    val name = readUTF()
    if (name == null || name.isEmpty) null else ResourceLocation.tryParse(name)
  }

  def readTileEntity[T: ClassTag](): Option[T] = {
    val dimension = readDimension()
    val x = readInt()
    val y = readInt()
    val z = readInt()
    getTileEntity(dimension, x, y, z)
  }

  def readEntity[T: ClassTag](): Option[T] = {
    val dimension = readDimension()
    val id = readInt()
    getEntity[T](dimension, id)
  }

  /** 方向：写侧写的是 `ordinal`，所以这里用 `Direction.values()(id)` 对称解析。 */
  def readDirection(): Option[Direction] = readByte() match {
    case id if id < 0 => None
    case id if id < Direction.values().length => Option(Direction.values()(id))
    case _ => None
  }

  /**
   * 读取物品堆栈。
   *
   * 与 1.7.10 一致：没有堆栈时返回 `null`（不改写调用方的 `if (stack != null)` 逻辑）。
   */
  def readItemStack(): ItemStack = {
    val haveStack = readBoolean()
    if (haveStack) {
      val nbt = readNBT()
      if (nbt != null) ItemStack.parseOptional(RegistryAccess.EMPTY, nbt) else null
    }
    else null
  }

  def readNBT(): CompoundTag = {
    val haveNbt = readBoolean()
    if (haveNbt) {
      NbtIo.read(this)
    }
    else null
  }

  def readMedium(): Int = {
    val c0 = readUnsignedByte()
    val c1 = readUnsignedByte()
    val c2 = readUnsignedByte()
    (c0) | (c1 << 8) | (c2 << 16)
  }

  def readPacketType(): PacketType.Value = PacketType(readByte())
}

/**
 * 包分发基类。
 *
 * 默认实现已经完全可用（注册表驱动）；如果需要临时加一段硬编码分派（移植过渡期），
 * 覆写 [dispatch] 即可。
 */
abstract class PacketHandler {
  /** 该处理器属于哪一侧：true = 服务端（处理 C→S 包），false = 客户端（处理 S→C 包）。 */
  protected def isServerSide: Boolean

  /** 为兼容原代码里 `p: PacketParser` 的写法（原版是内部类），这里加一个类型别名。 */
  type PacketParser = li.cil.oc.common.PacketParser

  /**
   * 顶层入口：由 NeoForge 的 payload 处理器在主线程调用（见 [OpenComputersNetwork]）。
   *
   * 数据布局：`[0|1][剩余负载]`，首字节为 1 表示剩余部分是 Deflater 压缩流。
   */
  private[oc] def onData(data: Array[Byte], player: Player): Unit = {
    // 不要因为畸形包崩服（可能被恶意客户端改过），只刷日志。
    var stream: InputStream = null
    try {
      stream = new ByteArrayInputStream(if (data == null) PacketHandler.emptyData else data)
      if (stream.read() != 0) stream = new InflaterInputStream(stream)
      dispatch(new PacketParser(stream, player, context(player)))
    } catch {
      case e: Throwable =>
        OpenComputers.log.warn("Received a badly formatted packet.", e)
    } finally {
      if (stream != null) {
        stream.close()
      }
    }

    // 避免 AFK 踢出：玩家在屏幕 GUI 里打字等操作也应算作活动。
    player match {
      case mp: ServerPlayer => mp.resetLastActionTime()
      case _ => // 客户端没有这个概念。
    }
  }

  /** 构造解析上下文（维度解析交给子类）。 */
  protected def context(player: Player): PacketContext =
    new PacketContext(player, isServerSide, dimension => world(player, dimension))

  /**
   * 取得指定维度对应的世界。
   *
   * 客户端：仅当维度与当前世界一致时返回该世界，否则 None。
   * 服务端：返回该维度对应的世界（若存在），否则 None。
   */
  protected def world(player: Player, dimension: ResourceLocation): Option[Level]

  /**
   * 按 `PacketType` 分派到注册表里的处理器。
   *
   * 找不到处理器时只写日志、不抛异常：server/client 包移植完成前，
   * 绝大多数包都会落到这个分支。
   */
  protected def dispatch(p: PacketParser): Unit = {
    PacketHandler.handlerFor(p.packetType, isServerSide) match {
      case Some(handler) =>
        try {
          handler(p, p.context)
        } catch {
          case e: Throwable => OpenComputers.log.warn(s"Error while handling packet ${p.packetType}.", e)
        }
      case None =>
        if (PacketHandler.expectedOn(p.packetType, isServerSide)) {
          PacketHandler.log.debug(s"No handler registered for ${p.packetType} on the ${p.context.sideName} side; ignoring.")
        } else {
          PacketHandler.log.warn(s"Received ${p.packetType} on the ${p.context.sideName} side, where it is not expected; ignoring.")
        }
    }
  }
}

object PacketHandler {
  val log = LogManager.getLogger(OpenComputers.Name + "-PacketHandler")

  /** 处理器签名：`(解析器, 上下文) => Unit`；解析器本身就是 `DataInputStream`。 */
  type HandlerFunction = (PacketParser, PacketContext) => Unit

  /** 方块实体回退解析器（例如机器人移动后留下的 `RobotAfterimage`）。 */
  type BlockEntityFallback = (Level, BlockPos) => Option[AnyRef]

  private val emptyData = new Array[Byte](0)

  private val serverHandlers = mutable.Map.empty[PacketType.Value, HandlerFunction]
  private val clientHandlers = mutable.Map.empty[PacketType.Value, HandlerFunction]
  private val blockEntityFallbacks = mutable.ArrayBuffer.empty[BlockEntityFallback]

  /**
   * 服务端侧应处理的包类型。
   *
   * 依据原 `li.cil.oc.server.PacketHandler.dispatch` 的 match 分支整理，
   * 仅用于日志提示（判断包是不是发错了方向），不参与分派。
   */
  val serverTypes: Set[PacketType.Value] = Set(
    PacketType.ComputerPower,
    PacketType.CopyToAnalyzer,
    PacketType.DriveLock,
    PacketType.DriveMode,
    PacketType.DronePower,
    PacketType.KeyDown,
    PacketType.KeyUp,
    PacketType.Clipboard,
    PacketType.MouseClickOrDrag,
    PacketType.MouseScroll,
    PacketType.MouseUp,
    PacketType.MultiPartPlace,
    PacketType.PetVisibility,
    PacketType.RackMountableMapping,
    PacketType.RackRelayState,
    PacketType.RobotAssemblerStart,
    PacketType.RobotStateRequest,
    PacketType.ServerPower,
    PacketType.TextBufferInit,
    PacketType.WaypointLabel
  )

  /**
   * 客户端侧应处理的包类型。
   *
   * 依据原 `li.cil.oc.client.PacketHandler.dispatch` 的 match 分支整理，用途同上。
   */
  val clientTypes: Set[PacketType.Value] = Set(
    PacketType.AbstractBusState,
    PacketType.AdapterState,
    PacketType.Analyze,
    PacketType.ChargerState,
    PacketType.ClientLog,
    PacketType.Clipboard,
    PacketType.ColorChange,
    PacketType.ComputerState,
    PacketType.ComputerUserList,
    PacketType.ContainerUpdate,
    PacketType.DisassemblerActiveChange,
    PacketType.FileSystemActivity,
    PacketType.FloppyChange,
    PacketType.HologramArea,
    PacketType.HologramClear,
    PacketType.HologramColor,
    PacketType.HologramPowerChange,
    PacketType.HologramRotation,
    PacketType.HologramRotationSpeed,
    PacketType.HologramScale,
    PacketType.HologramTranslation,
    PacketType.HologramValues,
    PacketType.LootDisk,
    PacketType.CyclingDisk,
    PacketType.NanomachinesConfiguration,
    PacketType.NanomachinesInputs,
    PacketType.NanomachinesPower,
    PacketType.NetSplitterState,
    PacketType.NetworkActivity,
    PacketType.ParticleEffect,
    PacketType.PetVisibility,
    PacketType.PowerState,
    PacketType.PrinterState,
    PacketType.RackInventory,
    PacketType.RackMountableData,
    PacketType.RaidStateChange,
    PacketType.RedstoneState,
    PacketType.RobotAnimateSwing,
    PacketType.RobotAnimateTurn,
    PacketType.RobotAssemblingState,
    PacketType.RobotInventoryChange,
    PacketType.RobotLightChange,
    PacketType.RobotMove,
    PacketType.RobotNameChange,
    PacketType.RobotSelectedSlotChange,
    PacketType.RotatableState,
    PacketType.SwitchActivity,
    PacketType.TextBufferInit,
    PacketType.TextBufferPowerChange,
    PacketType.TextBufferMulti,
    PacketType.ScreenTouchMode,
    PacketType.Sound,
    PacketType.SoundPattern,
    PacketType.TransposerActivity,
    PacketType.WaypointLabel
  )

  /**
   * 注册一个服务端侧（C→S）包处理器。
   *
   * 由 `li.cil.oc.server.PacketHandler` 移植完成后调用，例如
   * `PacketHandler.registerServer(PacketType.KeyDown, (p, _) => onKeyDown(p))`。
   */
  def registerServer(packetType: PacketType.Value, handler: HandlerFunction): Unit = {
    if (serverHandlers.contains(packetType)) {
      log.debug(s"Replacing the server handler of $packetType.")
    }
    serverHandlers(packetType) = handler
  }

  /**
   * 注册一个客户端侧（S→C）包处理器。
   *
   * 由 `li.cil.oc.client.PacketHandler` 移植完成后调用，例如
   * `PacketHandler.registerClient(PacketType.Sound, (p, _) => onSound(p))`。
   */
  def registerClient(packetType: PacketType.Value, handler: HandlerFunction): Unit = {
    if (clientHandlers.contains(packetType)) {
      log.debug(s"Replacing the client handler of $packetType.")
    }
    clientHandlers(packetType) = handler
  }

  /** 注销一个处理器（一般只在测试/重载时用）。 */
  def unregister(packetType: PacketType.Value): Unit = {
    serverHandlers.remove(packetType)
    clientHandlers.remove(packetType)
  }

  /** 该包类型在指定一侧是否已经有处理器。 */
  def isRegistered(packetType: PacketType.Value, isServerSide: Boolean): Boolean =
    (if (isServerSide) serverHandlers else clientHandlers).contains(packetType)

  /** 已注册的包类型数量（日志/调试用）。 */
  def registeredCount: Int = serverHandlers.size + clientHandlers.size

  /**
   * 注册「找不到方块实体时的回退解析器」。
   *
   * 原版在 `PacketParser.getTileEntity` 里查询 `RobotAfterimage`（机器人已经开始移动、
   * 方块实体已消失的情况）。`common/block` 尚未移植，所以改为注册式：
   * 移植完成后在 `RobotAfterimage` 的初始化里注册即可。
   */
  def registerBlockEntityFallback(fallback: BlockEntityFallback): Unit = blockEntityFallbacks += fallback

  private[oc] def handlerFor(packetType: PacketType.Value, isServerSide: Boolean): Option[HandlerFunction] =
    (if (isServerSide) serverHandlers else clientHandlers).get(packetType)

  private[oc] def findBlockEntityFallback(level: Level, pos: BlockPos): Option[AnyRef] =
    blockEntityFallbacks.iterator.map(fallback => fallback(level, pos)).collectFirst { case Some(value) => value }

  private[oc] def expectedOn(packetType: PacketType.Value, isServerSide: Boolean): Boolean =
    (if (isServerSide) serverTypes else clientTypes).contains(packetType)

  /**
   * 初始化网络层：把两侧入口接到 Java 网络层（`OpenComputersNetwork`）上并注册 payload。
   *
   * **必须由主类在 mod 事件总线上调用一次**（`li.cil.oc.OpenComputersNeo` 的构造函数）：
   * {{{
   *   PacketHandler.initialize(modBus)
   * }}}
   * 如果漏掉这一步，payload 不会被注册，NeoForge 会丢弃/拒绝本 mod 的所有网络包。
   */
  def initialize(modBus: IEventBus): Unit = {
    OpenComputersNetwork.setServerDispatcher((data: Array[Byte], player: Player) => ServerSide.onData(data, player))
    OpenComputersNetwork.setClientDispatcher((data: Array[Byte], player: Player) => ClientSide.onData(data, player))
    OpenComputersNetwork.register(modBus)
    log.info("Initialized OpenComputers network layer; registered handlers: server = {}, client = {}.", Int.box(serverHandlers.size), Int.box(clientHandlers.size))
  }

  /** 服务端默认处理器：维度解析走当前服务端。注册表驱动，无需 server 包。 */
  private[oc] object ServerSide extends PacketHandler {
    override protected def isServerSide: Boolean = true

    override protected def world(player: Player, dimension: ResourceLocation): Option[Level] =
      PacketHandler.serverWorld(dimension)
  }

  /** 客户端默认处理器：维度解析只匹配当前世界。注册表驱动，无需 client 包。 */
  private[oc] object ClientSide extends PacketHandler {
    override protected def isServerSide: Boolean = false

    override protected def world(player: Player, dimension: ResourceLocation): Option[Level] =
      PacketHandler.clientWorld(player, dimension)
  }

  /** 服务端：通过维度 id 取已加载的世界。 */
  private[oc] def serverWorld(dimension: ResourceLocation): Option[Level] = {
    if (dimension == null) None
    else {
      val server = ServerLifecycleHooks.getCurrentServer
      if (server == null) None
      else Option(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)))
    }
  }

  /** 客户端：只有当前世界与维度一致时才算命中。 */
  private[oc] def clientWorld(player: Player, dimension: ResourceLocation): Option[Level] = {
    if (dimension == null || player == null) None
    else Option(player.level()).filter(_.dimension().location() == dimension)
  }
}
