package li.cil.oc.server

import li.cil.oc.{Localization, OpenComputers, Settings, api}
import li.cil.oc.api.internal.Server
import li.cil.oc.common
import li.cil.oc.common.PacketType
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.container
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.common.tileentity._
import li.cil.oc.common.tileentity.traits.Computer
import li.cil.oc.server.machine.Machine
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import org.apache.logging.log4j.MarkerManager

/**
 * 服务端（C→S）网络包处理。
 *
 * ==1.21.1 迁移要点==
 * 1.7.10 里本类是 `li.cil.oc.common.PacketHandler`（抽象基类）的子类，靠覆写 `dispatch`
 * 做硬编码分派，并以 `@SubscribeEvent` 挂在 `FMLEventChannel` 上。
 *
 * 1.21.1 的链路改成了**注册表驱动**（见 [[li.cil.oc.common.PacketHandler]] 的说明）：
 *  - 解压、构造 `PacketParser`、维度解析、异常兜底全部由 common 层完成
 *    （服务端入口是 `PacketHandler.ServerSide`）；
 *  - 本 object 只负责「包类型 → 处理方法」的登记（[[initialize]]）与各个 `onXxx` 的实现，
 *    因此**不需要**再写 `dispatch`，也不再需要 `@SubscribeEvent`。
 *  - [[initialize]] 必须在服务端初始化时调用一次（由 [[Proxy.init]] 调用）。
 *
 * 「玩家真的有打开对应界面吗」的反伪造校验（原 `logForgedPacket`）保留了：
 *  - 计算机 / 机器人：1.7.10 比较 `container.otherInventory` 是不是同一个方块实体，
 *    1.21.1 的 `common.container.Player#otherInventory` 仍然就是宿主方块实体
 *    （它实现了 NeoForge 的 `IItemHandler`），所以改用引用相等（`eq`）判定，语义更严格；
 *  - 机架 / 机架上的服务器：比较 `container.Rack.rack` / `container.Server.server`
 *    与包里的方块实体是否为同一个。
 *
 * ==降级清单==
 *  - `PacketType.MultiPartPlace`：原实现依赖 ForgeMultipart 的 `integration.fmp.EventHandler`，
 *    1.21.1 无此模组，改为只读掉包体、不处理（见 [[onMultiPartPlace]]）。
 *  - 机器人装配完成时的成就：1.21.1 用 advancement 数据包取代了 `Achievement`，
 *    `common.Achievement` 不存在，见 [[onRobotAssemblerStart]] 的 TODO。
 *  - `Rack#isUseableByPlayer` / `Waypoint` 的距离校验：1.21.1 的方块实体没有
 *    `isUseableByPlayer`，统一换成等价的 8 格距离判定 [[withinInteractionRange]]。
 */
object PacketHandler {
  private val securityMarker = MarkerManager.getMarker("SuspiciousPackets")

  /** 为兼容原代码里 `p: PacketParser` 的写法提供的类型别名。 */
  private type PacketParser = common.PacketParser

  /**
   * 把服务端（C→S）的包处理器登记进 [[li.cil.oc.common.PacketHandler]] 的注册表。
   *
   * 由 [[Proxy.init]] 调用一次即可；重复调用是安全的（注册表按包类型覆盖）。
   */
  def initialize(): Unit = {
    common.PacketHandler.registerServer(PacketType.ComputerPower, (p, _) => onComputerPower(p))
    common.PacketHandler.registerServer(PacketType.CopyToAnalyzer, (p, _) => onCopyToAnalyzer(p))
    common.PacketHandler.registerServer(PacketType.DriveLock, (p, _) => onDriveLock(p))
    common.PacketHandler.registerServer(PacketType.DriveMode, (p, _) => onDriveMode(p))
    common.PacketHandler.registerServer(PacketType.DronePower, (p, _) => onDronePower(p))
    common.PacketHandler.registerServer(PacketType.KeyDown, (p, _) => onKeyDown(p))
    common.PacketHandler.registerServer(PacketType.KeyUp, (p, _) => onKeyUp(p))
    common.PacketHandler.registerServer(PacketType.Clipboard, (p, _) => onClipboard(p))
    common.PacketHandler.registerServer(PacketType.MouseClickOrDrag, (p, _) => onMouseClick(p))
    common.PacketHandler.registerServer(PacketType.MouseScroll, (p, _) => onMouseScroll(p))
    common.PacketHandler.registerServer(PacketType.MouseUp, (p, _) => onMouseUp(p))
    common.PacketHandler.registerServer(PacketType.MultiPartPlace, (p, _) => onMultiPartPlace(p))
    common.PacketHandler.registerServer(PacketType.PetVisibility, (p, _) => onPetVisibility(p))
    common.PacketHandler.registerServer(PacketType.RackMountableMapping, (p, _) => onRackMountableMapping(p))
    common.PacketHandler.registerServer(PacketType.RackRelayState, (p, _) => onRackRelayState(p))
    common.PacketHandler.registerServer(PacketType.RobotAssemblerStart, (p, _) => onRobotAssemblerStart(p))
    common.PacketHandler.registerServer(PacketType.RobotStateRequest, (p, _) => onRobotStateRequest(p))
    common.PacketHandler.registerServer(PacketType.ServerPower, (p, _) => onServerPower(p))
    common.PacketHandler.registerServer(PacketType.TextBufferInit, (p, _) => onTextBufferInit(p))
    common.PacketHandler.registerServer(PacketType.WaypointLabel, (p, _) => onWaypointLabel(p))
    OpenComputers.log.debug(s"Registered ${common.PacketHandler.serverTypes.size} server-side packet handlers.")
  }

  private def logForgedPacket(player: ServerPlayer): Unit =
    OpenComputers.log.warn(securityMarker, "Player {} tried to send GUI packets without opening them", player.getGameProfile)

  /**
   * 玩家是否还在与给定方块实体交互的距离内。
   *
   * TODO(port): 1.7.10 用的是方块实体的 `IInventory#isUseableByPlayer` /
   * `Container#canInteractWith`，1.21.1 的 OC 方块实体没有对应方法，
   * 这里退化为「8 格内」（与 [[onWaypointLabel]] 里的判定保持一致）。
   */
  private def withinInteractionRange(player: Player, t: BlockEntity): Boolean =
    t != null && player.distanceToSqr(t.getBlockPos.getX + 0.5, t.getBlockPos.getY + 0.5, t.getBlockPos.getZ + 0.5) <= 64.0

  // ----------------------------------------------------------------------- //
  // 电源
  // ----------------------------------------------------------------------- //

  def onComputerPower(p: PacketParser): Unit = {
    val entity = p.readTileEntity[Computer]()
    val setPower = p.readBoolean()
    p.player match {
      case player: ServerPlayer => (player.containerMenu, entity) match {
        // 1.7.10 比较的是 `container.otherInventory` 与包里的计算机坐标是否一致；
        // 1.21.1 的 `otherInventory` 就是宿主方块实体本身，直接做引用比较更严格。
        case (c: container.Player, Some(computer)) if c.otherInventory.asInstanceOf[AnyRef] eq computer =>
          trySetComputerPower(computer.machine, setPower, player)
        case _ => logForgedPacket(player)
      }
      case _ => // Invalid packet.
    }
  }

  def onServerPower(p: PacketParser): Unit = {
    val entity = p.readTileEntity[Rack]()
    val index = p.readInt()
    val readServer = entity match {
      case Some(t) =>
        t.getMountable(index) match {
          case server: Server => server
          case _ => return // probably just lag, not invalid packet
        }
      case _ => return
    }
    val setPower = p.readBoolean()
    p.player match {
      case player: ServerPlayer => player.containerMenu match {
        case c: container.Server => c.server match {
          case Some(server: Server) if server == readServer =>
            trySetComputerPower(server.machine, setPower, player)
          case _ => logForgedPacket(player)
        }
        case _ => logForgedPacket(player)
      }
      case _ => // Invalid packet.
    }
  }

  /**
   * 开 / 关机器。
   *
   * 1.21.1 里 `isPaused` / `start()` / `stop()` 位于
   * [[li.cil.oc.server.machine.Machine]]（`api.machine.Machine` 接口只保留只读查询），
   * 因此参数类型从 `api.machine.Machine` 换成它。
   * 玩家的「可交互」判定由 `getCommandSenderName` 改为 `getScoreboardName`
   * （1.21.1 里名字即档案名）。
   */
  private def trySetComputerPower(computer: api.machine.Machine, value: Boolean, player: ServerPlayer): Unit = {
    // `canInteract` / `isPaused` / `start` / `stop` 只在服务端实现
    // [[li.cil.oc.server.machine.Machine]] 上，`api.machine.Machine` 接口只暴露只读查询
    // （`lastError` 等），因此这里先收窄到具体类型。
    computer match {
      case machine: Machine =>
        if (machine.canInteract(player.getScoreboardName)) {
          if (value) {
            if (!machine.isPaused) {
              machine.start()
              machine.lastError match {
                case message if message != null => player.displayClientMessage(Localization.Analyzer.LastError(message), false)
                case _ =>
              }
            }
          }
          else machine.stop()
        }
      case _ => // 不是本项目的机器实现（理论上不会发生）。
    }
  }

  // ----------------------------------------------------------------------- //
  // 分析器 / 手持物品
  // ----------------------------------------------------------------------- //

  def onCopyToAnalyzer(p: PacketParser): Unit = {
    val text = p.readUTF()
    val line = p.readInt()
    // 1.21.1：`Player#worldObj` → `Player#level()`。
    ComponentTracker.get(p.player.level(), text) match {
      case Some(buffer: TextBuffer) => buffer.copyToAnalyzer(line, p.player)
      case _ => // Invalid Packet
    }
  }

  def onDriveLock(p: PacketParser): Unit = p.player match {
    case player: ServerPlayer =>
      // 1.21.1：`getHeldItem` → `getMainHandItem`。
      val heldItem = player.getMainHandItem
      Delegator.subItem(heldItem) match {
        case Some(drive: FileSystemLike) => DriveData.lock(heldItem, player)
        case _ => // Invalid packet
      }
    case _ => // Invalid Packet
  }

  def onDriveMode(p: PacketParser): Unit = {
    val unmanaged = p.readBoolean()
    p.player match {
      case player: ServerPlayer =>
        val heldItem = player.getMainHandItem
        Delegator.subItem(heldItem) match {
          case Some(drive: FileSystemLike) => DriveData.setUnmanaged(heldItem, unmanaged)
          case _ => // Invalid packet.
        }
      case _ => // Invalid packet.
    }
  }

  def onDronePower(p: PacketParser): Unit = {
    val entity = p.readTileEntity[Drone]()
    val power = p.readBoolean()
    p.player match {
      case player: ServerPlayer => (player.containerMenu, entity) match {
        case (c: container.Drone, Some(readDrone)) if c.drone == readDrone =>
          val drone = c.drone
          if (power) {
            drone.preparePowerUp()
          }
          trySetComputerPower(drone.machine, power, player)
        case _ => logForgedPacket(player)
      }
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //
  // 屏幕 / 文本缓冲
  // ----------------------------------------------------------------------- //

  def onKeyDown(p: PacketParser): Unit = {
    val address = p.readUTF()
    val key = p.readChar()
    val code = p.readInt()
    ComponentTracker.get(p.player.level(), address) match {
      case Some(buffer: api.internal.TextBuffer) => buffer.keyDown(key, code, p.player)
      case _ => // Invalid Packet
    }
  }

  def onKeyUp(p: PacketParser): Unit = {
    val address = p.readUTF()
    val key = p.readChar()
    val code = p.readInt()
    ComponentTracker.get(p.player.level(), address) match {
      case Some(buffer: api.internal.TextBuffer) => buffer.keyUp(key, code, p.player)
      case _ => // Invalid Packet
    }
  }

  def onClipboard(p: PacketParser): Unit = {
    val address = p.readUTF()
    val copy = p.readUTF()
    if (copy.length > Settings.get.maxClipboardTextLength) return
    ComponentTracker.get(p.player.level(), address) match {
      case Some(buffer: api.internal.TextBuffer) => buffer.clipboard(copy, p.player)
      case _ => // Invalid Packet
    }
  }

  def onMouseClick(p: PacketParser): Unit = {
    val address = p.readUTF()
    val x = p.readFloat()
    val y = p.readFloat()
    val dragging = p.readBoolean()
    val button = p.readByte()
    ComponentTracker.get(p.player.level(), address) match {
      case Some(buffer: api.internal.TextBuffer) =>
        if (dragging) buffer.mouseDrag(x, y, button, p.player)
        else buffer.mouseDown(x, y, button, p.player)
      case _ => // Invalid Packet
    }
  }

  def onMouseUp(p: PacketParser): Unit = {
    val address = p.readUTF()
    val x = p.readFloat()
    val y = p.readFloat()
    val button = p.readByte()
    ComponentTracker.get(p.player.level(), address) match {
      case Some(buffer: api.internal.TextBuffer) => buffer.mouseUp(x, y, button, p.player)
      case _ => // Invalid Packet
    }
  }

  def onMouseScroll(p: PacketParser): Unit = {
    val address = p.readUTF()
    val x = p.readFloat()
    val y = p.readFloat()
    val button = p.readByte()
    ComponentTracker.get(p.player.level(), address) match {
      case Some(buffer: api.internal.TextBuffer) => buffer.mouseScroll(x, y, button, p.player)
      case _ => // Invalid Packet
    }
  }

  /**
   * 把方块实体里的文本缓冲同步给打开界面的玩家（客户端据此重建屏幕内容）。
   */
  def onTextBufferInit(p: PacketParser): Unit = {
    val address = p.readUTF()
    p.player match {
      case entity: ServerPlayer =>
        ComponentTracker.get(p.player.level(), address) match {
          case Some(buffer: TextBuffer) =>
            if (buffer.host match {
              case screen: Screen if !screen.isOrigin => false
              case _ => true
            }) {
              val nbt = new CompoundTag()
              buffer.data.save(nbt)
              nbt.putInt("maxWidth", buffer.getMaximumWidth)
              nbt.putInt("maxHeight", buffer.getMaximumHeight)
              nbt.putInt("viewportWidth", buffer.getViewportWidth)
              nbt.putInt("viewportHeight", buffer.getViewportHeight)
              PacketSender.sendTextBufferInit(address, nbt, entity)
            }
          case _ => // Invalid packet.
        }
      case _ => // Invalid packet.
    }
  }

  // ----------------------------------------------------------------------- //
  // 杂项 / 多方块
  // ----------------------------------------------------------------------- //

  /**
   * ForgeMultipart 的「放置微方块」转发。
   *
   * TODO(port): 1.7.10 里是 `integration.fmp.EventHandler.place(player)`
   * （把玩家手里的 ForgeMultipart 部件放到机器人 / 无人机指向的位置）。
   * 1.21.1 没有 ForgeMultipart，`li.cil.oc.integration.fmp` 也不存在，
   * 因此这里只把包体读完并丢弃。若之后要支持其它微方块模组，请在此处重新接线。
   */
  def onMultiPartPlace(p: PacketParser): Unit = {
    // 包体为空，无需读取；保留参数以维持与原分派表一一对应。
  }

  def onPetVisibility(p: PacketParser): Unit = {
    val value = p.readBoolean()
    p.player match {
      case player: ServerPlayer =>
        // 1.21.1：`getCommandSenderName` → `getScoreboardName`。
        val name = player.getScoreboardName
        if (if (value) {
          PetVisibility.hidden.remove(name)
        }
        else {
          PetVisibility.hidden.add(name)
        }) {
          // Something changed.
          PacketSender.sendPetVisibility(Some(name))
        }
      case _ => // Invalid packet.
    }
  }

  def onRackMountableMapping(p: PacketParser): Unit = {
    val entity = p.readTileEntity[Rack]()
    val mountableIndex = p.readInt()
    val nodeIndex = p.readInt()
    val side = p.readDirection()
    p.player match {
      case player: ServerPlayer => (player.containerMenu, entity) match {
        case (c: container.Rack, Some(readRack)) if readRack == c.rack =>
          if (withinInteractionRange(player, readRack))
            readRack.connect(mountableIndex, nodeIndex - 1, side)
        case _ => logForgedPacket(player)
      }
      case _ =>
    }
  }

  def onRackRelayState(p: PacketParser): Unit = {
    val entity = p.readTileEntity[Rack]()
    val enabled = p.readBoolean()
    entity match {
      case Some(t) => p.player match {
        case player: ServerPlayer if withinInteractionRange(player, t) =>
          t.isRelayEnabled = enabled
        case _ =>
      }
      case _ => // Invalid packet.
    }
  }

  def onRobotAssemblerStart(p: PacketParser): Unit = {
    val entity = p.readTileEntity[Assembler]()
    entity match {
      case Some(assembler) =>
        if (assembler.start(p.player match {
          // 1.21.1：`player.capabilities.isCreativeMode` → `player.isCreative`。
          case player: ServerPlayer => player.isCreative
          case _ => false
        })) {
          // TODO(port): 原为 `assembler.output.foreach(stack => Achievement.onAssemble(stack, p.player))`。
          // 1.21.1 把成就换成了 advancement 数据包，`li.cil.oc.common.Achievement` 已不存在。
          // 恢复时应在 `data/opencomputers_neo/advancement/` 里定义对应 advancement，
          // 并在这里用 `ServerPlayer#getAdvancements` 触发（需要 criterion trigger）。
        }
      case _ => // Invalid packet.
    }
  }

  def onRobotStateRequest(p: PacketParser): Unit = {
    p.readTileEntity[RobotProxy]() match {
      // 1.21.1：`world.markBlockForUpdate(x, y, z)` → 方块实体自己的 `markBlockForUpdate()`。
      case Some(proxy) => proxy.markBlockForUpdate()
      case _ => // Invalid packet.
    }
  }

  def onWaypointLabel(p: PacketParser): Unit = {
    val entity = p.readTileEntity[Waypoint]()
    val label = p.readUTF().take(32)
    entity match {
      case Some(waypoint) => p.player match {
        case player: ServerPlayer if player.distanceToSqr(waypoint.x + 0.5, waypoint.y + 0.5, waypoint.z + 0.5) <= 64 =>
          if (label != waypoint.label) {
            waypoint.label = label
            PacketSender.sendWaypointLabel(waypoint)
          }
        case _ =>
      }
      case _ => // Invalid packet.
    }
  }
}
