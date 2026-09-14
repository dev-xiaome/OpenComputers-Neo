package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.component.RackMountable
import li.cil.oc.api.internal
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.ComponentHost
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, IntArrayTag, Tag}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import scala.jdk.CollectionConverters._

/**
 * 机架方块实体（原 1.7.10 `common.tileentity.Rack`）：4 个槽位，可安装服务器 / 交换机 /
 * 磁盘驱动器等「机架可装载物」（[[RackMountable]]），并可作为组件总线在各面之间中继。
 *
 * 纹理：正面 = RackFront（4 个装载槽），其它 = RackSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数只有 `(pos, state)`，方块实体类型由方块反查。
 *  - `updateEntity()` → [[traits.TileEntity#tick]]（覆写时先调 `super.tick()`）。
 *  - `getSizeInventory` → `getSlots`、`getInventoryStackLimit` → `getSlotLimit`、
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `ForgeDirection` → `Direction`（没有 `UNKNOWN`：`nodeMapping` 的「无连接」用 `None` 表示，
 *    序列化时写 `-1`；原来用于表示「未知」的旧序号 3 也一并按 `None` 处理）。
 *  - `NBTTagIntArray#func_150302_c()` → `IntArrayTag#getAsIntArray`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）；客户端专用逻辑用注释标注。
 *
 * ==降级清单==
 *  - `server.PacketSender`（`sendRackInventory` / `sendRackMountableData`）→ 方块更新 + 同步标签。
 *  - `integration.opencomputers.DriverRedstoneCard` / `integration.stargatetech2.DriverAbstractBusCard`
 *    → `hasRedstoneCard` / `hasAbstractBusCard` 恒为 `false`。
 *  - `integration.Mods`（StargateTech2 抽象总线设备注册）→ 已整体移除。
 */
class Rack(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.PowerAcceptor
    with traits.Hub
    with traits.PowerBalancer
    with traits.ComponentInventory
    with traits.Rotatable
    with traits.BundledRedstoneAware
    with traits.AbstractBusAware
    with Analyzable
    with internal.Rack
    with traits.StateAware {

  var isRelayEnabled = false
  val lastData = new Array[CompoundTag](getSlots)
  val hasChanged = Array.fill(getSlots)(true)

  // Map node connections for each installed mountable. Each mountable may
  // have up to four outgoing connections, with the first one always being
  // the "primary" connection, i.e. being a direct connection allowing
  // component access (i.e. actually connecting to that side of the rack).
  // The other nodes are "secondary" connections and merely transfer network
  // messages.
  // mountable -> connectable -> side
  val nodeMapping = Array.fill(getSlots)(Array.fill[Option[Direction]](4)(None))
  val snifferNodes = Array.fill(getSlots)(Array.fill(3)(api.Network.newNode(this, Visibility.Neighbors).create()))

  def connect(slot: Int, connectableIndex: Int, side: Option[Direction]): Unit = {
    // 1.21.1 的 `Direction` 没有 `UNKNOWN`；旧代码里表示「无连接」的 SOUTH 序号也是
    // `Direction.SOUTH`（+Z），因此两者都映射为 `None`。
    val newSide = side match {
      case Some(direction) if direction != Direction.SOUTH => Option(direction)
      case _ => None
    }

    val oldSide = nodeMapping(slot)(connectableIndex + 1)
    if (oldSide == newSide) return

    // Cut connection / remove sniffer node.
    val mountable = getMountable(slot)
    if (mountable != null && oldSide.isDefined) {
      if (connectableIndex == -1) {
        val node = mountable.node
        val plug = sidedNode(toGlobal(oldSide.get))
        if (node != null && plug != null) {
          node.disconnect(plug)
        }
      }
      else if (connectableIndex >= 0) {
        snifferNodes(slot)(connectableIndex).remove()
      }
    }

    nodeMapping(slot)(connectableIndex + 1) = newSide

    // Establish connection / add sniffer node.
    if (mountable != null && newSide.isDefined) {
      if (connectableIndex == -1) {
        val node = mountable.node
        val plug = sidedNode(toGlobal(newSide.get))
        if (node != null && plug != null) {
          node.connect(plug)
        }
      }
      else if (connectableIndex >= 0 && connectableIndex < mountable.getConnectableCount) {
        val connectable = mountable.getConnectableAt(connectableIndex)
        if (connectable != null && connectable.node != null) {
          if (connectable.node.network == null) {
            api.Network.joinNewNetwork(connectable.node)
          }
          connectable.node.connect(snifferNodes(slot)(connectableIndex))
        }
      }
    }
  }

  private def reconnect(plugSide: Direction): Unit = {
    for (slot <- 0 until getSlots) {
      val mapping = nodeMapping(slot)
      mapping(0) match {
        case Some(side) if toGlobal(side) == plugSide =>
          val mountable = getMountable(slot)
          val busNode = sidedNode(plugSide)
          if (busNode != null && mountable != null && mountable.node != null && busNode != mountable.node) {
            api.Network.joinNewNetwork(mountable.node)
            busNode.connect(mountable.node)
          }
        case _ => // Not connected to this side.
      }
      for (connectableIndex <- 0 until 3) {
        mapping(connectableIndex + 1) match {
          case Some(side) if toGlobal(side) == plugSide =>
            val mountable = getMountable(slot)
            if (mountable != null && connectableIndex < mountable.getConnectableCount) {
              val connectable = mountable.getConnectableAt(connectableIndex)
              if (connectable != null && connectable.node != null) {
                if (connectable.node.network == null) {
                  api.Network.joinNewNetwork(connectable.node)
                }
                connectable.node.connect(snifferNodes(slot)(connectableIndex))
              }
            }
          case _ => // Not connected to this side.
        }
      }
    }
  }

  protected def sendPacketToMountables(sourceSide: Option[Direction], packet: Packet): Unit = {
    // When a message arrives on a bus, also send it to all secondary nodes
    // connected to it. Only deliver it to that very node, if it's not the
    // sender, to avoid loops.
    for (slot <- 0 until getSlots) {
      val mapping = nodeMapping(slot)
      for (connectableIndex <- 0 until 3) {
        mapping(connectableIndex + 1) match {
          case Some(side) if sourceSide.contains(toGlobal(side)) =>
            val mountable = getMountable(slot)
            if (mountable != null && connectableIndex < mountable.getConnectableCount) {
              val connectable = mountable.getConnectableAt(connectableIndex)
              if (connectable != null) {
                connectable.receivePacket(packet)
              }
            }
          case _ => // Not connected to a bus.
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // Hub

  override def tryEnqueuePacket(sourceSide: Option[Direction], packet: Packet): Boolean = {
    sendPacketToMountables(sourceSide, packet)
    if (isRelayEnabled)
      super.tryEnqueuePacket(sourceSide, packet)
    else
      true
  }

  override protected def relayPacket(sourceSide: Option[Direction], packet: Packet): Unit = {
    if (isRelayEnabled)
      super.relayPacket(sourceSide, packet)
  }

  override protected def onPlugConnect(plug: Plug, node: Node): Unit = {
    super.onPlugConnect(plug, node)
    connectComponents()
    reconnect(plug.side)
  }

  protected override def createNode(plug: Plug): Node = api.Network.newNode(plug, Visibility.Network)
    .withConnector(Settings.get.bufferDistributor)
    .create()

  // ----------------------------------------------------------------------- //
  // Environment

  override def dispose(): Unit = {
    super.dispose()
    disconnectComponents()
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "network.message") message.data match {
      case Array(packet: Packet) => relayIfMessageFromConnectable(message, packet)
      case _ =>
    }
  }

  private def relayIfMessageFromConnectable(message: Message, packet: Packet): Unit = {
    for (slot <- 0 until getSlots) {
      val mountable = getMountable(slot)
      if (mountable != null) {
        val mapping = nodeMapping(slot)
        for (connectableIndex <- 0 until 3) {
          mapping(connectableIndex + 1) match {
            case Some(side) =>
              if (connectableIndex < mountable.getConnectableCount) {
                val connectable = mountable.getConnectableAt(connectableIndex)
                if (connectable != null && connectable.node == message.source) {
                  val busNode = sidedNode(toGlobal(side))
                  if (busNode != null) busNode.sendToReachable("network.message", packet)
                  relayToConnectablesOnSide(message, packet, side)
                  return
                }
              }
            case _ => // Not connected to a bus.
          }
        }
      }
    }
  }

  private def relayToConnectablesOnSide(message: Message, packet: Packet, sourceSide: Direction): Unit = {
    for (slot <- 0 until getSlots) {
      val mountable = getMountable(slot)
      if (mountable != null) {
        val mapping = nodeMapping(slot)
        for (connectableIndex <- 0 until 3) {
          mapping(connectableIndex + 1) match {
            case Some(side) if side == sourceSide =>
              if (connectableIndex < mountable.getConnectableCount) {
                val connectable = mountable.getConnectableAt(connectableIndex)
                if (connectable != null && connectable.node != message.source) {
                  snifferNodes(slot)(connectableIndex).sendToNeighbors("network.message", packet)
                }
              }
            case _ => // Not connected to a bus.
          }
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // SidedEnvironment

  override def canConnect(side: Direction): Boolean = side != facing

  override def sidedNode(side: Direction): Node = if (side != facing) super.sidedNode(side) else null

  // ----------------------------------------------------------------------- //
  // power.Common

  // 仅客户端调用；1.7.10 的 `@SideOnly(Side.CLIENT)` 已删除。
  override protected def hasConnector(side: Direction): Boolean = side != facing

  override protected def connector(side: Direction): Option[Connector] =
    Option(if (side != facing) sidedNode(side).asInstanceOf[Connector] else null)

  override def energyThroughput: Double = Settings.get.serverRackRate

  // ----------------------------------------------------------------------- //
  // Analyzable

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    slotAt(Direction.from3DDataValue(side), hitX, hitY, hitZ) match {
      case Some(slot) => components(slot) match {
        case Some(analyzable: Analyzable) => analyzable.onAnalyze(player, side, hitX, hitY, hitZ)
        case _ => null
      }
      case _ => Array(sidedNode(Direction.from3DDataValue(side)))
    }
  }

  // ----------------------------------------------------------------------- //
  // AbstractBusAware

  override def installedComponents: Iterable[ManagedEnvironment] = components.collect {
    case Some(mountable: RackMountable with ComponentHost) =>
      mountable.getComponents.asScala.collect { case managed: ManagedEnvironment => managed }
  }.flatten.toIndexedSeq

  /**
   * 抽象总线接口查询。
   *
   * TODO(integration.stargatetech2): 原实现（带 `@Optional.Method(modid = StargateTech2)`）
   * 在非正面时返回 `super.getInterfaces(side)`（由 JVM 注入的接口提供）。
   * 1.21.1 已移除 ASM 注入与 StargateTech2 集成，这里保持「正面无接口」的语义并返回空数组。
   */
  override def getInterfaces(side: Int): Array[AnyRef] = Array.empty[AnyRef]

  override def getWorld = world

  // ----------------------------------------------------------------------- //
  // internal.Rack

  override def indexOfMountable(mountable: RackMountable): Int = components.indexWhere(_.contains(mountable))

  override def getMountable(slot: Int): RackMountable = components(slot) match {
    case Some(mountable: RackMountable) => mountable
    case _ => null
  }

  override def getMountableData(slot: Int): CompoundTag = lastData(slot)

  override def markChanged(slot: Int): Unit = {
    hasChanged.synchronized(hasChanged(slot) = true)
    setOutputEnabled(hasRedstoneCard)
    isAbstractBusAvailable = hasAbstractBusCard
  }

  // ----------------------------------------------------------------------- //
  // StateAware

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    val result = util.EnumSet.noneOf(classOf[api.util.StateAware.State])
    components.collect {
      case Some(mountable: RackMountable) => result.addAll(mountable.getCurrentState)
    }
    result
  }

  // ----------------------------------------------------------------------- //
  // Rotatable

  override protected def onRotationChanged(): Unit = {
    super.onRotationChanged()
    checkRedstoneInputChanged()
  }

  // ----------------------------------------------------------------------- //
  // RedstoneAware

  override protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    components.collect {
      case Some(mountable: RackMountable) if mountable.node != null =>
        val toLocalArgs = RedstoneChangedEventArgs(toLocal(args.side), args.oldValue, args.newValue, args.color)
        mountable.node.sendToNeighbors("redstone.changed", toLocalArgs)
    }
  }

  // ----------------------------------------------------------------------- //
  // IItemHandler（原 `IInventory` / `ISidedInventory`）

  override def getSlots: Int = 4

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (_, Some(driver)) => driver.slot(stack) == Slot.RackMountable
    case _ => false
  }

  override def markDirty(): Unit = {
    super.markDirty()
    if (isServer) {
      setOutputEnabled(hasRedstoneCard)
      isAbstractBusAvailable = hasAbstractBusCard
      // TODO(server.PacketSender): 原为 ServerPacketSender.sendRackInventory(this)。
      markBlockForUpdate()
    }
    else {
      markBlockForUpdate()
    }
  }

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    if (isServer) {
      for (connectable <- 0 until 4) {
        nodeMapping(slot)(connectable) = None
      }
      lastData(slot) = null
      hasChanged(slot) = true
    }
    super.onItemAdded(slot, stack)
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    if (isServer) {
      for (connectable <- 0 until 4) {
        nodeMapping(slot)(connectable) = None
      }
      lastData(slot) = null
    }
    super.onItemRemoved(slot, stack)
  }

  override protected def connectItemNode(node: Node): Unit = {
    // By default create a new network for mountables. They have to
    // be wired up manually (mapping is reset in onItemAdded).
    api.Network.joinNewNetwork(node)
  }

  // ----------------------------------------------------------------------- //
  // BlockEntity

  override def tick(): Unit = {
    super.tick()
    if (isServer && isConnected) {
      lazy val connectors = Direction.values().map(sidedNode).collect {
        case connector: Connector => connector
      }
      components.zipWithIndex.collect {
        case (Some(mountable: RackMountable), slot) =>
          if (hasChanged(slot)) {
            hasChanged(slot) = false
            lastData(slot) = mountable.getData
            // TODO(server.PacketSender): 原为 ServerPacketSender.sendRackMountableData(this, slot)。
            markBlockForUpdate()
            notifyNeighbors()
            // These are working state dependent, so recompute them.
            setOutputEnabled(hasRedstoneCard)
            isAbstractBusAvailable = hasAbstractBusCard
          }

          // Power mountables without requiring them to be connected to the outside.
          mountable.node match {
            case connector: Connector =>
              var remaining = Settings.get.serverRackRate
              for (outside <- connectors if remaining > 0) {
                val received = remaining + outside.changeBuffer(-remaining)
                val rejected = connector.changeBuffer(received)
                outside.changeBuffer(rejected)
                remaining -= received - rejected
              }
            case _ => // Nothing using energy.
          }
      }

      updateComponents()
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)

    isRelayEnabled = nbt.getBoolean(Settings.namespace + "isRelayEnabled")
    nbt.getList(Settings.namespace + "nodeMapping", Tag.TAG_INT_ARRAY).map((buses: IntArrayTag) =>
      buses.getAsIntArray.map(id => if (id < 0 || id >= Direction.values().length) None else Option(Direction.from3DDataValue(id)))).
      copyToArray(nodeMapping)

    // Kickstart initialization.
    _isOutputEnabled = hasRedstoneCard
    _isAbstractBusAvailable = hasAbstractBusCard
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)

    nbt.putBoolean(Settings.namespace + "isRelayEnabled", isRelayEnabled)
    nbt.setNewTagList(Settings.namespace + "nodeMapping", nodeMapping.map(buses =>
      new IntArrayTag(buses.map(side => side.map(_.ordinal()).getOrElse(-1)))).toIndexedSeq)
  }

  /** 仅客户端使用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。 */
  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)

    val data = nbt.getList(Settings.namespace + "lastData", Tag.TAG_COMPOUND).
      toArray[CompoundTag]
    data.copyToArray(lastData)
    load(nbt.getCompound(Settings.namespace + "rackData"))
    connectComponents()
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)

    val data = lastData.map(tag => if (tag == null) new CompoundTag() else tag)
    nbt.setNewTagList(Settings.namespace + "lastData", data.toIndexedSeq)
    nbt.setNewCompoundTag(Settings.namespace + "rackData", tag => save(tag))
  }

  // ----------------------------------------------------------------------- //

  def slotAt(side: Direction, hitX: Float, hitY: Float, hitZ: Float): Option[Int] = {
    if (side == facing) {
      val globalY = (hitY * 16).toInt // [0, 15]
      val l = 2
      val h = 14
      val slot = ((15 - globalY) - l) * getSlots / (h - l)
      Some(math.max(0, math.min(getSlots - 1, slot)))
    }
    else None
  }

  def isWorking(mountable: RackMountable): Boolean =
    mountable.getCurrentState.contains(api.util.StateAware.State.IsWorking)

  /**
   * 机架内是否装有抽象总线卡。
   *
   * TODO(integration.stargatetech2): 原实现遍历机架装载物的物品栏，用
   * `DriverAbstractBusCard.worksWith` 判定；该集成包未纳入本次编译范围，恒为 `false`。
   */
  def hasAbstractBusCard = false

  /**
   * 机架内是否装有红石卡。
   *
   * TODO(integration.opencomputers): 原实现遍历机架装载物的物品栏，用
   * `DriverRedstoneCard.worksWith` 判定；该集成包未纳入本次编译范围，恒为 `false`，
   * 因此机架目前不会向外界输出红石信号。
   */
  def hasRedstoneCard = false

  /** 机架装载物的宿主（供 `EnvironmentHost` 使用）。 */
  private def environmentHost: EnvironmentHost = this
}
