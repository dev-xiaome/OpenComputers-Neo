package li.cil.oc.common.blockentity

import li.cil.oc.{OpenComputers, Settings, api}
import li.cil.oc.api.component.RackMountable
import li.cil.oc.api.{Driver, internal}
import li.cil.oc.api.network._
import li.cil.oc.api.util.StateAware
import li.cil.oc.client.renderer.block.ServerRackModel
import li.cil.oc.common.blockentity.traits.RedstoneChangedEventArgs
import li.cil.oc.common.component.{RackKVM, TerminalServer}
import li.cil.oc.common.datacomponents.{CompoundStorage, OCComponents}
import li.cil.oc.common.{Slot, menu}
import li.cil.oc.integration.opencomputers.DriverRedstoneCard
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedInventory._
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.{DataComponentHolder, DataComponentMap, DataComponentPatch}
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.nbt.{CompoundTag, IntArrayTag, Tag}
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.{Container, MenuProvider}
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import net.neoforged.neoforge.client.model.data.ModelData
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import java.util
import scala.collection.immutable.ArraySeq

class Rack(pos: BlockPos, state: BlockState)
  extends BlockEntity(BlockEntityTypes.RACK.get(), pos, state) with traits.PowerAcceptor with traits.Hub with traits.PowerBalancer
    with traits.ComponentInventory with traits.Rotatable with traits.BundledRedstoneAware with Analyzable with internal.Rack with traits.StateAware with MenuProvider
    with IBlockEntityExtension {

  var isRelayEnabled = false
  val lastData: Array[Option[CompoundStorage]] = Array.fill[Option[CompoundStorage]](getContainerSize) { None }
  val hasChanged: Array[Boolean] = Array.fill(getContainerSize)(true)

  @OnlyIn(Dist.CLIENT)
  override def getModelData: ModelData =
    ModelData.builder()
      .`with`(ServerRackModel.RACK_PROPERTY, this)
      .build()

  // Map node connections for each installed mountable. Each mountable may
  // have up to four outgoing connections, with the first one always being
  // the "primary" connection, i.e. being a direct connection allowing
  // component access (i.e. actually connecting to that side of the rack).
  // The other nodes are "secondary" connections and merely transfer network
  // messages.
  // mountable -> connectable -> side
  val nodeMapping: Array[Array[Option[Direction]]] = Array.fill(getContainerSize)(Array.fill[Option[Direction]](4)(None))
  val snifferNodes: Array[Array[Node]] = Array.fill(getContainerSize)(Array.fill(3)(api.Network.newNode(this, Visibility.Neighbors).create()))

  def connect(slot: Int, connectableIndex: Int, side: Option[Direction]): Unit = {
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
    for (slot <- 0 until getContainerSize) {
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
    for (slot <- 0 until getContainerSize) {
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
    for (slot <- 0 until getContainerSize) {
      val mountable = getMountable(slot)
      if (mountable != null) {
        val mapping = nodeMapping(slot)
        for (connectableIndex <- 0 until 3) {
          mapping(connectableIndex + 1) match {
            case Some(side) =>
              if (connectableIndex < mountable.getConnectableCount) {
                val connectable = mountable.getConnectableAt(connectableIndex)
                if (connectable != null && connectable.node == message.source) {
                  sidedNode(toGlobal(side)).sendToReachable("network.message", packet)
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
    for (slot <- 0 until getContainerSize) {
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

  @OnlyIn(Dist.CLIENT)
  override protected def hasConnector(side: Direction): Boolean = side != facing

  override protected def connector(side: Direction) = Option(if (side != facing) sidedNode(side).asInstanceOf[Connector] else null)

  override def energyThroughput: Double = Settings.get.serverRackRate

  // ----------------------------------------------------------------------- //
  // Analyzable

  override def onAnalyze(player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    slotAt(side, hitX, hitY, hitZ) match {
      case Some(slot) => componentSlots(slot) match {
        case Some(analyzable: Analyzable) => analyzable.onAnalyze(player, side, hitX, hitY, hitZ)
        case _ => null
      }
      case _ => Array(sidedNode(side))
    }
  }

  // ----------------------------------------------------------------------- //
  // internal.Rack

  override def indexOfMountable(mountable: RackMountable): Int = componentSlots.indexWhere(_.contains(mountable))

  override def getMountable(slot: Int): RackMountable = componentSlots(slot) match {
    case Some(mountable: RackMountable) => mountable
    case _ => null
  }

  override def getMountableData(slot: Int): DataComponentHolder = lastData(slot) getOrElse CompoundStorage.EMPTY

  override def markChanged(slot: Int): Unit = {
    hasChanged.synchronized(hasChanged(slot) = true)
    setOutputEnabled(hasRedstoneCard)
  }

  // ----------------------------------------------------------------------- //
  // StateAware

  override def getCurrentState: util.EnumSet[StateAware.State] = {
    val result = util.EnumSet.noneOf(classOf[api.util.StateAware.State])
    componentSlots.collect {
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
    componentSlots.collect {
      case Some(mountable: RackMountable) if mountable.node != null =>
        val toLocalArgs = RedstoneChangedEventArgs(toLocal(args.side), args.oldValue, args.newValue, args.color)
        mountable.node.sendToNeighbors("redstone.changed", toLocalArgs)
    }
  }

  // ----------------------------------------------------------------------- //
  // IInventory

  override def getContainerSize = 4

  override def getMaxStackSize = 1

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (_, Some(driver)) => driver.slot(stack) == Slot.RackMountable
    case _ => false
  }

  override def setChanged(): Unit = {
    super.setChanged()
    if (isServer) {
      setOutputEnabled(hasRedstoneCard)
      ServerPacketSender.sendRackInventory(this)
    }
    else {
      getLevel.sendBlockUpdated(getBlockPos, getLevel.getBlockState(getBlockPos), getLevel.getBlockState(getBlockPos), 3)
    }
  }

  // ----------------------------------------------------------------------- //
  // INamedContainerProvider

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new menu.Rack(id, playerInventory, this)

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    if (isServer) {
      for (connectable <- 0 until 4) {
        nodeMapping(slot)(connectable) = None
      }
      lastData(slot) = None
      hasChanged(slot) = true
    }
    super.onItemAdded(slot, stack)
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    if (isServer) {
      for (connectable <- 0 until 4) {
        nodeMapping(slot)(connectable) = None
      }
      lastData(slot) = None
    }
    super.onItemRemoved(slot, stack)
  }

  override protected def connectItemNode(node: Node): Unit = {
    // By default create a new network for mountables. They have to
    // be wired up manually (mapping is reset in onItemAdded).
    api.Network.joinNewNetwork(node)
  }

  // ----------------------------------------------------------------------- //
  // TileEntity

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (isServer && isConnected) {
      lazy val connectors = ArraySeq.unsafeWrapArray(Direction.values()).map(sidedNode).collect {
        case connector: Connector => connector
      }
      componentSlots.zipWithIndex.collect {
        case (Some(mountable: RackMountable), slot) =>
          if (hasChanged(slot)) {
            hasChanged(slot) = false

            val data = lastData(slot) getOrElse new CompoundStorage()
            mountable.describeForClient(data)
            lastData(slot) = Some(data)

            ServerPacketSender.sendRackMountableData(this, slot)
            getLevel.updateNeighborsAt(getBlockPos, getBlockState.getBlock)
            // These are working state dependent, so recompute them.
            setOutputEnabled(hasRedstoneCard)
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
    else {
      // A terminal's input packets are routed directly by its virtual screen
      // address, but its display changes are flushed from TerminalServer.update.
      // Keep that one mountable ticking while the rack's outer network is not
      // connected; otherwise input continues to work while display updates
      // accumulate until the next full snapshot. On the client this also keeps
      // initialization retries alive during world loading.
      componentSlots.foreach {
        case Some(terminal: TerminalServer) => terminal.update()
        case Some(kvm: RackKVM) => kvm.update()
        case _ =>
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def loadComponentsForServer(holder: DataComponentHolder): Unit = {
    super.loadComponentsForServer(holder)
    isRelayEnabled = holder.has(OCComponents.RELAY_ENABLED)

    for(nodeMap <- holder.getComponent(OCComponents.RACK_NODE_MAPPING)) {
      nodeMap.map(_.map {
        case Direction.SOUTH => None
        case other => Some(other)
      }.toArray).toArray copyToArray nodeMapping
    }
  }

  override def saveComponentsForServer(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForServer(holder)
    holder.setComponent(OCComponents.RELAY_ENABLED, isRelayEnabled)
    holder.setComponent(OCComponents.RACK_NODE_MAPPING, nodeMapping.map(_.map {
      case None => Direction.SOUTH
      case Some(Direction.SOUTH) =>
        OpenComputers.log.warn(s"Weird direction value in rack at $pos! SOUTH should not be possible?")
        Direction.SOUTH
      case Some(other) => other
    }.toList).toList)
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    // Inventory.saveForServer writes mountable ItemStacks to chunk NBT before
    // saveComponentsForServer normally flushes their live environments. Only
    // the terminal server needs its nested virtual screen/keyboard snapshot at
    // this earlier point. Do not flush rack-mounted Servers here: doing so can
    // disturb their machine lifecycle and saved running state.
    componentSlots.zip(items).foreach {
      case (Some(terminal: TerminalServer), stack) if !stack.isEmpty => terminal.saveData(stack)
      case (Some(kvm: RackKVM), stack) if !stack.isEmpty => kvm.saveData(stack)
      case _ =>
    }
    super.saveForServer(nbt, provider)
  }

  override def loadComponentsForClient(holder: DataComponentHolder): Unit = {
    super.loadComponentsForClient(holder)
    requestModelDataUpdate()

    for(data <- holder.getComponent(OCComponents.RACK_DATA)) {
      data.toArray.copyToArray(lastData)
    }

    loadData(holder)
    connectComponents()
  }

  override def saveComponentsForClient(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForClient(holder)
    holder.setComponent(OCComponents.RACK_DATA, lastData.toList)

    saveData(holder)
  }
  // ----------------------------------------------------------------------- //

  def slotAt(side: Direction, hitX: Float, hitY: Float, hitZ: Float): Option[Int] = {
    if (side == facing) {
      val globalY = (hitY * 16).toInt // [0, 15]
      val l = 2
      val h = 14
      val slot = ((15 - globalY) - l) * getContainerSize / (h - l)
      Some(math.max(0, math.min(getContainerSize - 1, slot)))
    }
    else None
  }

  def isWorking(mountable: RackMountable): Boolean = mountable.getCurrentState.contains(api.util.StateAware.State.IsWorking)

  def hasRedstoneCard: Boolean = componentSlots.exists {
    case Some(mountable: EnvironmentHost with RackMountable with Container) if isWorking(mountable) =>
      mountable.exists(stack => DriverRedstoneCard.worksWith(stack, mountable.getClass))
    case _ => false
  }
}
