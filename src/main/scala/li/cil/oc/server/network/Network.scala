package li.cil.oc.server.network

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network
import li.cil.oc.api.network._
import li.cil.oc.api.network.{Node => ImmutableNode}
import li.cil.oc.common.tileentity
import li.cil.oc.server.network.{Component => MutableComponent}
import li.cil.oc.server.network.{ComponentConnector => MutableComponentConnector}
import li.cil.oc.server.network.{Connector => MutableConnector}
import li.cil.oc.server.network.{Node => MutableNode}
import li.cil.oc.util.Color
import li.cil.oc.util.SideTracker
import net.minecraft.core.Direction
import net.minecraft.nbt._
import net.minecraft.world.level.block.entity.BlockEntity

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// Looking at this again after some time, the similarity to const in C++ is somewhat uncanny.
private class Network private(private val data: mutable.Map[String, Network.Vertex] = mutable.Map.empty) extends Distributor {
  def this(node: MutableNode) = {
    this()
    addNew(node)
    node.onConnect(node)
  }

  var globalBuffer = 0.0

  var globalBufferSize = 0.0

  private val connectors = mutable.ArrayBuffer.empty[MutableConnector]

  private lazy val wrapper = new Network.Wrapper(this)

  data.values.foreach(node => {
    node.data match {
      case connector: MutableConnector => addConnector(connector)
      case _ =>
    }
    node.data.network = wrapper
  })

  // Called by nodes when they want to change address from loading.
  def remap(remappedNode: MutableNode, newAddress: String): Unit = {
    data.get(remappedNode.address) match {
      case Some(node) =>
        val neighbors = node.edges.map(_.other(node))
        node.data.remove()
        node.data.address = newAddress
        while (data.contains(node.data.address)) {
          node.data.address = java.util.UUID.randomUUID().toString
        }
        if (neighbors.isEmpty)
          addNew(node.data)
        else
          neighbors.foreach(_.data.connect(node.data))
      case _ => throw new AssertionError("Node believes it belongs to a network it doesn't.")
    }
  }

  // ----------------------------------------------------------------------- //

  def connect(nodeA: MutableNode, nodeB: MutableNode) = {
    if (nodeA == null) throw new NullPointerException("nodeA")
    if (nodeB == null) throw new NullPointerException("nodeB")

    if (nodeA == nodeB) throw new IllegalArgumentException(
      "Cannot connect a node to itself.")

    val containsA = contains(nodeA)
    val containsB = contains(nodeB)

    if (!containsA && !containsB) throw new IllegalArgumentException(
      "At least one of the nodes must already be in this network.")

    lazy val oldNodeA = node(nodeA)
    lazy val oldNodeB = node(nodeB)

    if (containsA && containsB) {
      // Both nodes already exist in the network but there is a new connection.
      // This can happen if a new node sequentially connects to multiple nodes
      // in an existing network, e.g. in a setup like so:
      // O O   Where O is an old node, and N is the new Node. It would connect
      // O N   to the node above and left to it (in no particular order).
      if (!oldNodeA.edges.exists(_.isBetween(oldNodeA, oldNodeB))) {
        assert(!oldNodeB.edges.exists(_.isBetween(oldNodeA, oldNodeB)))
        Network.Edge(oldNodeA, oldNodeB)
        if (oldNodeA.data.reachability == Visibility.Neighbors)
          oldNodeB.data.onConnect(oldNodeA.data)
        if (oldNodeB.data.reachability == Visibility.Neighbors)
          oldNodeA.data.onConnect(oldNodeB.data)
        true
      }
      else false // That connection already exists.
    }
    else if (containsA) add(oldNodeA, nodeB)
    else add(oldNodeB, nodeA)
  }

  def disconnect(nodeA: MutableNode, nodeB: MutableNode) = {
    if (nodeA == nodeB) throw new IllegalArgumentException(
      "Cannot disconnect a node from itself.")

    val containsA = contains(nodeA)
    val containsB = contains(nodeB)

    if (!containsA || !containsB) throw new IllegalArgumentException(
      "Both of the nodes must be in this network.")

    def oldNodeA = node(nodeA)
    def oldNodeB = node(nodeB)

    oldNodeA.edges.find(_.isBetween(oldNodeA, oldNodeB)) match {
      case Some(edge) =>
        handleSplit(edge.remove())
        if (edge.left.data.reachability == Visibility.Neighbors)
          edge.right.data.onDisconnect(edge.left.data)
        if (edge.right.data.reachability == Visibility.Neighbors)
          edge.left.data.onDisconnect(edge.right.data)
        true
      case _ => false // That connection doesn't exists.
    }
  }

  def remove(node: MutableNode) = {
    data.remove(node.address) match {
      case Some(entry) =>
        node match {
          case connector: MutableConnector => removeConnector(connector)
          case _ =>
        }
        node.network = null
        val subGraphs = entry.remove()
        val targets = Iterable(node) ++ (entry.data.reachability match {
          case Visibility.None => Iterable.empty[ImmutableNode]
          case Visibility.Neighbors => entry.edges.map(_.other(entry).data)
          case Visibility.Network => subGraphs.flatMap(_.values.map(_.data))
        })
        handleSplit(subGraphs)
        targets.foreach(_.asInstanceOf[MutableNode].onDisconnect(node))
        true
      case _ => false
    }
  }

  // ----------------------------------------------------------------------- //

  def node(address: String) = {
    data.get(address) match {
      case Some(node) => node.data
      case _ => null
    }
  }

  def nodes: Iterable[ImmutableNode] = data.values.map(_.data)

  def reachableNodes(reference: ImmutableNode): scala.collection.Iterable[ImmutableNode] = {
    // 与 `neighbors(reference)`（返回 `java.lang.Iterable`）区分开：
    // 内部一律用 Scala 集合，避免在 `api.network._` 通配导入下 `Iterable` 名字歧义。
    val referenceNeighbors = directNeighbors(reference)
    nodes.filter(node => node != reference && (node.reachability == Visibility.Network ||
      (node.reachability == Visibility.Neighbors && referenceNeighbors.contains(node))))
  }

  def reachingNodes(reference: ImmutableNode): scala.collection.Iterable[ImmutableNode] = {
    if (reference.reachability == Visibility.Network) nodes.filter(node => node != reference)
    else if (reference.reachability == Visibility.Neighbors) {
      val referenceNeighbors = directNeighbors(reference)
      nodes.filter(node => node != reference && referenceNeighbors.contains(node))
    } else scala.collection.Iterable.empty
  }

  /** 内部使用的邻居查询（Scala 集合版）。 */
  private def directNeighbors(node: ImmutableNode): Set[ImmutableNode] = {
    data.get(node.address) match {
      case Some(n) if n.data == node => n.edges.map(_.other(n).data).toSet
      case _ => throw new IllegalArgumentException("Node must be in this network.")
    }
  }

  def neighbors(node: ImmutableNode): java.lang.Iterable[ImmutableNode] = {
    data.get(node.address) match {
      case Some(n) if n.data == node => NodeCollections.toJavaCollection(n.edges.map(_.other(n).data))
      case _ => throw new IllegalArgumentException("Node must be in this network.")
    }
  }

  // ----------------------------------------------------------------------- //

  def sendToAddress(source: ImmutableNode, target: String, name: String, args: AnyRef*) = {
    if (source.network != wrapper)
      throw new IllegalArgumentException("Source node must be in this network.")
    data.get(target) match {
      case Some(node) if node.data.canBeReachedFrom(source) =>
        send(source, Iterable(node.data), name, args.toSeq: _*)
      case _ =>
    }
  }

  def sendToNeighbors(source: ImmutableNode, name: String, args: AnyRef*) = {
    if (source.network != wrapper)
      throw new IllegalArgumentException("Source node must be in this network.")
    send(source, directNeighbors(source).filter(_.reachability != Visibility.None), name, args.toSeq: _*)
  }

  def sendToReachable(source: ImmutableNode, name: String, args: AnyRef*) = {
    if (source.network != wrapper)
      throw new IllegalArgumentException("Source node must be in this network.")
    send(source, reachableNodes(source), name, args.toSeq: _*)
  }

  def sendToVisible(source: ImmutableNode, name: String, args: AnyRef*) = {
    if (source.network != wrapper)
      throw new IllegalArgumentException("Source node must be in this network.")
    send(source, reachableNodes(source) collect {
      case component: api.network.Component if component.canBeSeenFrom(source) => component
    }, name, args.toSeq: _*)
  }

  // ----------------------------------------------------------------------- //

  private def contains(node: MutableNode) = node.network == wrapper && data.contains(node.address)

  private def node(node: ImmutableNode) = data(node.address)

  private def addNew(node: MutableNode) = {
    val newNode = new Network.Vertex(node)
    if (node.address == null || data.contains(node.address))
      node.address = java.util.UUID.randomUUID().toString
    data += node.address -> newNode
    node match {
      case connector: MutableConnector => addConnector(connector)
      case _ =>
    }
    node.network = wrapper
    newNode
  }

  private def add(oldNode: Network.Vertex, addedNode: MutableNode): Boolean = {
    // Queue onConnect calls to avoid side effects from callbacks.
    val connects = mutable.Buffer.empty[(ImmutableNode, Iterable[ImmutableNode])]
    // Check if the other node is new or if we have to merge networks.
    if (addedNode.network == null) {
      val newNode = addNew(addedNode)
      Network.Edge(oldNode, newNode)
      addedNode.reachability match {
        case Visibility.None =>
          connects += ((addedNode, Iterable(addedNode)))
        case Visibility.Neighbors =>
          connects += ((addedNode, Iterable(addedNode) ++ NodeCollections.toScala(neighbors(addedNode))))
          reachingNodes(addedNode).foreach(node => connects += ((node, Iterable(addedNode))))
        case Visibility.Network =>
          // Explicitly send to the added node itself first.
          connects += ((addedNode, Iterable(addedNode) ++ nodes.filter(_ != addedNode)))
          reachingNodes(addedNode).foreach(node => connects += ((node, Iterable(addedNode))))
      }

      // added node may load more internal nodes
      addedNode.onConnect(addedNode)
      val visibleNodes = nodes.filter(_.reachability == Visibility.Network)
      visibleNodes.foreach(node => connects += ((node, nodes)))
    }
    else {
      val otherNetwork = addedNode.network.asInstanceOf[Network.Wrapper].network

      // If the other network contains nodes with addresses used in our local
      // network we'll have to re-assign those... since dynamically handling
      // changes to one's address is not expected of nodes / hosts, we have to
      // remove and reconnect the nodes. This is a pretty shitty solution, and
      // may break things slightly here and there (e.g. if this is the node of
      // a running machine the computer will most likely crash), but it should
      // never happen in normal operation anyway. It *can* happen when NBT
      // editing stuff or using mods to clone blocks (e.g. WorldEdit).
      val duplicates = otherNetwork.data.filter(entry => data.contains(entry._1)).values.toArray
      val otherNetworkAfterReaddress = if (duplicates.isEmpty) {
        otherNetwork
      } else {
        duplicates.foreach(vertex => {
          val node = vertex.data
          val neighbors = vertex.edges.map(_.other(vertex).data).toArray

          var newAddress = ""
          do {
            newAddress = java.util.UUID.randomUUID().toString
          } while (data.contains(newAddress) || otherNetwork.data.contains(newAddress))

          // This may lead to splits, which is the whole reason we have to
          // check the network of the other nodes after the readdressing.
          node.remove()
          node.address = newAddress
          Network.joinNewNetwork(node)

          if (node.address == newAddress) {
            neighbors.filter(_.network != null).foreach(_.connect(node))
          } else {
            OpenComputers.log.error("I can't see this happening any other way than someone directly setting node addresses, which they shouldn't. So yeah. Shit'll be borked. Deal with it.")
            node.remove() // well screw you then
          }
        })

        duplicates.head.data.network.asInstanceOf[Network.Wrapper].network
      }

      // The address change can theoretically cause the node to be kicked from
      // its old network (via onConnect callbacks), so we make sure it's still
      // in the same network. If it isn't we start over.
      if (addedNode.network != null && addedNode.network.asInstanceOf[Network.Wrapper].network == otherNetworkAfterReaddress) {
        if (addedNode.reachability == Visibility.Neighbors)
          connects += ((addedNode, Iterable(oldNode.data)))
        if (oldNode.data.reachability == Visibility.Neighbors)
          connects += ((oldNode.data, Iterable(addedNode)))

        val oldNodes = nodes
        val newNodes = otherNetworkAfterReaddress.nodes
        val oldVisibleNodes = oldNodes.filter(_.reachability == Visibility.Network)
        val newVisibleNodes = newNodes.filter(_.reachability == Visibility.Network)

        newVisibleNodes.foreach(node => connects += ((node, oldNodes)))
        oldVisibleNodes.foreach(node => connects += ((node, newNodes)))

        data ++= otherNetworkAfterReaddress.data
        connectors ++= otherNetworkAfterReaddress.connectors
        globalBuffer += otherNetworkAfterReaddress.globalBuffer
        globalBufferSize += otherNetworkAfterReaddress.globalBufferSize
        otherNetworkAfterReaddress.data.values.foreach(node => {
          node.data match {
            case connector: MutableConnector => connector.distributor = Some(wrapper)
            case _ =>
          }
          node.data.network = wrapper
        })
        otherNetworkAfterReaddress.data.clear()
        otherNetworkAfterReaddress.connectors.clear()

        Network.Edge(oldNode, node(addedNode))
      }
      else add(oldNode, addedNode)
    }

    for ((node, nodes) <- connects) nodes.foreach(_.asInstanceOf[MutableNode].onConnect(node))

    true
  }

  private def handleSplit(subGraphs: Seq[mutable.Map[String, Network.Vertex]]) =
    if (subGraphs.size > 1) {
      val nodes = subGraphs.map(_.values.map(_.data))
      val visibleNodes = nodes.map(_.filter(_.reachability == Visibility.Network))

      data.clear()
      connectors.clear()
      globalBuffer = 0
      globalBufferSize = 0
      data ++= subGraphs.head
      for (node <- data.values) node.data match {
        case connector: MutableConnector => addConnector(connector)
        case _ =>
      }
      subGraphs.tail.foreach(new Network(_))

      for (indexA <- subGraphs.indices) {
        val nodesA = nodes(indexA)
        val visibleNodesA = visibleNodes(indexA)
        for (indexB <- (indexA + 1) until subGraphs.length) {
          val nodesB = nodes(indexB)
          val visibleNodesB = visibleNodes(indexB)
          visibleNodesA.foreach(node => nodesB.foreach(_.onDisconnect(node)))
          visibleNodesB.foreach(node => nodesA.foreach(_.onDisconnect(node)))
        }
      }
    }

  private def send(source: ImmutableNode, targets: Iterable[ImmutableNode], name: String, args: AnyRef*): Unit = {
    val message = new Network.Message(source, name, Array(args.toSeq: _*))
    targets.foreach(_.host.onMessage(message))
  }

  // ----------------------------------------------------------------------- //

  def addConnector(connector: MutableConnector): Unit = {
    if (connector.localBufferSize > 0) {
      assert(!connectors.contains(connector))
      connectors += connector
      globalBuffer += connector.localBuffer
      globalBufferSize += connector.localBufferSize
    }
    connector.distributor = Some(wrapper)
  }

  def removeConnector(connector: MutableConnector): Unit = {
    if (connector.localBufferSize > 0) {
      assert(connectors.contains(connector))
      connectors -= connector
      globalBuffer -= connector.localBuffer
      globalBufferSize -= connector.localBufferSize
    }
  }

  def changeBuffer(delta: Double): Double = {
    if (delta == 0) 0
    else if (Settings.get.ignorePower) {
      if (delta < 0) 0
      else /* if (delta > 0) */ delta
    }
    else this.synchronized {
      val oldBuffer = globalBuffer
      globalBuffer = math.min(math.max(globalBuffer + delta, 0), globalBufferSize)
      if (globalBuffer == oldBuffer) {
        return delta
      }
      if (delta < 0) {
        var remaining = -delta
        for (connector <- connectors if remaining > 0) {
          if (connector.localBuffer > 0) {
            if (connector.localBuffer < remaining) {
              remaining -= connector.localBuffer
              connector.localBuffer = 0
            }
            else {
              connector.localBuffer -= remaining
              remaining = 0
            }
          }
        }
        -remaining
      }
      else /* if (delta > 0) */ {
        var remaining = delta
        for (connector <- connectors if remaining > 0) {
          if (connector.localBuffer < connector.localBufferSize) {
            val space = connector.localBufferSize - connector.localBuffer
            if (space < remaining) {
              remaining -= space
              connector.localBuffer = connector.localBufferSize
            }
            else {
              connector.localBuffer += remaining
              remaining = 0
            }
          }
        }
        remaining
      }
    }
  }
}

object Network extends api.detail.NetworkAPI {
  override def joinOrCreateNetwork(tileEntity: BlockEntity): Unit =
    if (!tileEntity.isRemoved && tileEntity.getLevel != null && !tileEntity.getLevel.isClientSide) {
      val level = tileEntity.getLevel
      val origin = tileEntity.getBlockPos
      for (side <- Direction.values()) {
        val pos = origin.relative(side)
        if (level.isLoaded(pos)) {
          val localNode = getNetworkNode(tileEntity, side)
          val neighborTileEntity = level.getBlockEntity(pos)
          val neighborNode = getNetworkNode(neighborTileEntity, side.getOpposite)
          localNode match {
            case Some(node: MutableNode) =>
              neighborNode match {
                case Some(neighbor: MutableNode) if neighbor != node && neighbor.network != null =>
                  val canConnectColor = canConnectBasedOnColor(tileEntity, neighborTileEntity)
                  // 1.7.10 这里还有 FMP（ForgeMultipart）与 Immibis 微方块的遮挡判定。
                  // 两者都依赖 `codechicken.multipart.*` / 第三方 API，未移植到 1.21.1
                  // （见 `common/block/Cable.scala` 的说明），因此对应判定整体删除，
                  // 连线是否成立只看「驱动 + 颜色」。
                  if (canConnectColor) neighbor.connect(node)
                  else node.disconnect(neighbor)
                case _ =>
              }
              if (node.network == null) {
                joinNewNetwork(node)
              }
            case _ =>
          }
        }
      }
    }

  def joinNewNetwork(node: ImmutableNode): Unit = node match {
    case mutableNode: MutableNode if mutableNode.network == null =>
      new Network(mutableNode)
    case _ =>
  }

  def getNetworkNode(tileEntity: BlockEntity, side: Direction): Option[ImmutableNode] =
    tileEntity match {
      case host: SidedEnvironment => Option(host.sidedNode(side))
      case host: Environment with SidedComponent =>
        if (host.canConnectNode(side)) Option(host.node)
        else None
      case host: Environment => Option(host.node)
      case _ => None
    }

  private def cableColor(tileEntity: BlockEntity): Int =
    tileEntity match {
      case cable: tileentity.Cable => cable.color
      case _ => Color.LightGray
    }

  private def canConnectBasedOnColor(te1: BlockEntity, te2: BlockEntity): Boolean = {
    val (c1, c2) = (cableColor(te1), cableColor(te2))
    c1 == c2 || c1 == Color.LightGray || c2 == Color.LightGray
  }

  // ----------------------------------------------------------------------- //

  override def joinWirelessNetwork(endpoint: WirelessEndpoint): Unit = {
    WirelessNetwork.add(endpoint)
  }

  override def updateWirelessNetwork(endpoint: WirelessEndpoint): Unit = {
    WirelessNetwork.update(endpoint)
  }

  override def leaveWirelessNetwork(endpoint: WirelessEndpoint): Unit = {
    WirelessNetwork.remove(endpoint)
  }

  override def leaveWirelessNetwork(endpoint: WirelessEndpoint, dimension: Int): Unit = {
    WirelessNetwork.remove(endpoint, dimension)
  }

  // ----------------------------------------------------------------------- //

  override def sendWirelessPacket(source: WirelessEndpoint, strength: Double, packet: network.Packet): Unit = {
    for (endpoint <- WirelessNetwork.computeReachableFrom(source, strength)) {
      endpoint.receivePacket(packet, source)
    }
  }

  // ----------------------------------------------------------------------- //

  def newNode(host: Environment, reachability: Visibility) = new NodeBuilder(host, reachability)

  override def newPacket(source: String, destination: String, port: Int, data: Array[AnyRef]) = {
    val packet = new Packet(source, destination, port, data)
    // We do the size check here instead of in the constructor of the packet
    // itself to avoid errors when loading packets.
    if (packet.size > Settings.get.maxNetworkPacketSize) {
      throw new IllegalArgumentException("packet too big (max " + Settings.get.maxNetworkPacketSize + ")")
    }
    packet
  }

  override def newPacket(nbt: CompoundTag) = {
    val source = nbt.getString("source")
    val destination =
      if (nbt.contains("dest")) null
      else nbt.getString("dest")
    // 1.21.1：`getInteger` → `getInt`，`hasKey` → `contains`，`getTag` → `get`。
    val port = nbt.getInt("port")
    val ttl = nbt.getInt("ttl")
    val data = (for (i <- 0 until nbt.getInt("dataLength")) yield {
      if (nbt.contains("data" + i)) {
        // 原实现读的是 1.7.10 混淆方法名 `func_15029x_`，1.21.1 统一为
        // `NumericTag#getAsXxx` / `StringTag#getAsString` / `ByteArrayTag#getAsByteArray`。
        nbt.get("data" + i) match {
          case boolean: ByteTag => Boolean.box(boolean.getAsByte == 1)
          case short: ShortTag => Short.box(short.getAsShort)
          case integer: IntTag => Int.box(integer.getAsInt)
          case long: LongTag => Long.box(long.getAsLong)
          case float: FloatTag => Float.box(float.getAsFloat)
          case double: DoubleTag => Double.box(double.getAsDouble)
          case string: StringTag => string.getAsString: AnyRef
          case array: ByteArrayTag => array.getAsByteArray
        }
      }
      else null
    }).toArray
    new Packet(source, destination, port, data, ttl)
  }

  var isServer = SideTracker.isServer _

  class NodeBuilder(val _host: Environment, val _reachability: Visibility) extends api.detail.Builder.NodeBuilder {
    def withComponent(name: String, visibility: Visibility) = new Network.ComponentBuilder(_host, _reachability, name, visibility)

    def withComponent(name: String) = withComponent(name, _reachability)

    def withConnector(bufferSize: Double) = new Network.ConnectorBuilder(_host, _reachability, bufferSize)

    def withConnector() = withConnector(0)

    def create() = if (isServer()) new MutableNode with NodeVarargPart {
      val host = _host
      val reachability = _reachability
    }
    else null
  }

  class ComponentBuilder(val _host: Environment, val _reachability: Visibility, val _name: String, val _visibility: Visibility) extends api.detail.Builder.ComponentBuilder {
    def withConnector(bufferSize: Double) = new Network.ComponentConnectorBuilder(_host, _reachability, _name, _visibility, bufferSize)

    def withConnector() = withConnector(0)

    // 注意：必须用本包的 `Component`（`li.cil.oc.server.network.Component`）。
    // 文件顶部的 `import li.cil.oc.api.network._` 会把同名 Java 接口 `Component` 引入作用域，
    // 而通配 import 的优先级高于同包不同编译单元的成员，直接用 `Component` 会解析到接口上，
    // 导致 "object creation impossible"。
    def create() = if (isServer()) new MutableComponent with NodeVarargPart {
      val host = _host
      val reachability = _reachability
      val name = _name
      setVisibility(_visibility)
    }
    else null
  }

  class ConnectorBuilder(val _host: Environment, val _reachability: Visibility, val bufferSize: Double) extends api.detail.Builder.ConnectorBuilder {
    def withComponent(name: String, visibility: Visibility) = new Network.ComponentConnectorBuilder(_host, _reachability, name, visibility, bufferSize)

    def withComponent(name: String) = withComponent(name, _reachability)

    def create() = if (isServer()) new MutableConnector with NodeVarargPart {
      val host = _host
      val reachability = _reachability
      // 用 setter 而不是直接赋值 `localBufferSize = bufferSize`：
      // 外层 builder 的同名字段 `bufferSize` 会让匿名类体里的标识符解析产生歧义，
      // 显式走 `api.network.Connector#setLocalBufferSize` 更稳。
      setLocalBufferSize(bufferSize)
    }
    else null
  }

  class ComponentConnectorBuilder(val _host: Environment, val _reachability: Visibility, val _name: String, val _visibility: Visibility, val bufferSize: Double) extends api.detail.Builder.ComponentConnectorBuilder {
    def create() = if (isServer()) new MutableComponentConnector with NodeVarargPart {
      val host = _host
      val reachability = _reachability
      val name = _name
      setLocalBufferSize(bufferSize)
      setVisibility(_visibility)
    }
    else null
  }

  // ----------------------------------------------------------------------- //

  private class Vertex(val data: MutableNode) {
    val edges = ArrayBuffer.empty[Edge]

    def remove() = {
      edges.foreach(edge => edge.other(this).edges -= edge)
      searchGraphs(edges.map(_.other(this)).toSeq)
    }

    override def toString = s"$data [${edges.length}]"
  }

  private case class Edge(left: Vertex, right: Vertex) {
    left.edges += this
    right.edges += this

    def other(side: Vertex) = if (side == left) right else left

    def isBetween(a: Vertex, b: Vertex) = (a == left && b == right) || (b == left && a == right)

    def remove() = {
      left.edges -= this
      right.edges -= this
      searchGraphs(List(left, right))
    }
  }

  private def searchGraphs(seeds: Seq[Vertex]) = {
    val seen = mutable.Set.empty[Vertex]
    seeds.map(seed => {
      if (seen.contains(seed)) None
      else {
        val addressed = mutable.Map.empty[String, Vertex]
        val queue = mutable.Queue(seed)
        while (queue.nonEmpty) {
          val node = queue.dequeue()
          seen += node
          addressed += node.data.address -> node
          queue ++= node.edges.map(_.other(node)).filter(n => !seen.contains(n) && !queue.contains(n))
        }
        Some(addressed)
      }
    }) filter (_.nonEmpty) map (_.get)
  }

  // ----------------------------------------------------------------------- //

  private class Message(val source: ImmutableNode, val name: String, val data: Array[AnyRef]) extends api.network.Message {
    var isCanceled = false

    def cancel() = isCanceled = true
  }

  // ----------------------------------------------------------------------- //

  class Packet(var source: String, var destination: String, var port: Int, var data: Array[AnyRef], var ttl: Int = Settings.get.initialNetworkPacketTTL) extends api.network.Packet {
    val size = Option(data).fold(0)(values => {
      if (values.length > Settings.get.maxNetworkPacketParts) {
        throw new IllegalArgumentException("packet has too many parts")
      }
      values.length * 2 + values.foldLeft(0)((acc, arg) => {
        acc + (arg match {
          // Scala 2.13：`Unit` 伴生对象不能再作为模式使用，unit 值改用守卫判断。
          case null | None => 1
          case _ if arg == () => 1
          case _: java.lang.Boolean => 1
          case _: java.lang.Byte => 2 /* FIXME: Bytes are currently sent as shorts */
          case _: java.lang.Short => 2
          case _: java.lang.Integer => 4
          case _: java.lang.Long => 8
          case _: java.lang.Float => 4
          case _: java.lang.Double => 8
          case value: java.lang.String => value.length max 1
          case value: Array[Byte] => value.length max 1
          case _ => throw new IllegalArgumentException("unsupported data type")
        })
      })
    })

    override def hop() = new Packet(source, destination, port, data, ttl - 1)

    override def save(nbt: CompoundTag): Unit = {
      nbt.putString("source", source)
      if (destination != null && !destination.isEmpty) {
        nbt.putString("dest", destination)
      }
      nbt.putInt("port", port)
      nbt.putInt("ttl", ttl)
      nbt.putInt("dataLength", data.length)
      for (i <- data.indices) data(i) match {
        // Scala 2.13：`Unit` 伴生对象不能再作为模式使用，unit 值改用守卫判断。
        case null | None =>
        case _ if data(i) == () =>
        case value: java.lang.Boolean => nbt.putBoolean("data" + i, value)
        case value: java.lang.Byte => nbt.putShort("data" + i, value.shortValue)
        case value: java.lang.Short => nbt.putShort("data" + i, value)
        case value: java.lang.Integer => nbt.putInt("data" + i, value)
        case value: java.lang.Long => nbt.putLong("data" + i, value)
        case value: java.lang.Float => nbt.putFloat("data" + i, value)
        case value: java.lang.Double => nbt.putDouble("data" + i, value)
        case value: java.lang.String => nbt.putString("data" + i, value)
        case value: Array[Byte] => nbt.putByteArray("data" + i, value)
        case value => OpenComputers.log.warn("Unexpected type while saving network packet: " + value.getClass.getName)
      }
    }

    override def toString = s"{source = $source, destination = $destination, port = $port, data = [${data.mkString(", ")}}]}"
  }

  // ----------------------------------------------------------------------- //

  private[network] class Wrapper(val network: Network) extends api.network.Network with Distributor {
    def connect(nodeA: ImmutableNode, nodeB: ImmutableNode) =
      network.connect(nodeA.asInstanceOf[MutableNode], nodeB.asInstanceOf[MutableNode])

    def disconnect(nodeA: ImmutableNode, nodeB: ImmutableNode) =
      network.disconnect(nodeA.asInstanceOf[MutableNode], nodeB.asInstanceOf[MutableNode])

    def remove(node: ImmutableNode) = network.remove(node.asInstanceOf[MutableNode])

    def node(address: String) = network.node(address)

    def nodes = NodeCollections.toJavaCollection(network.nodes)

    def nodes(reference: ImmutableNode) = NodeCollections.toJavaCollection(network.reachableNodes(reference))

    def neighbors(node: ImmutableNode) = NodeCollections.toJavaCollection(NodeCollections.toScala(network.neighbors(node)))

    def sendToAddress(source: ImmutableNode, target: String, name: String, data: AnyRef*) =
      network.sendToAddress(source, target, name, data.toSeq: _*)

    def sendToNeighbors(source: ImmutableNode, name: String, data: AnyRef*) =
      network.sendToNeighbors(source, name, data.toSeq: _*)

    def sendToReachable(source: ImmutableNode, name: String, data: AnyRef*) =
      network.sendToReachable(source, name, data.toSeq: _*)

    def sendToVisible(source: ImmutableNode, name: String, data: AnyRef*) =
      network.sendToVisible(source, name, data.toSeq: _*)

    def globalBuffer = network.globalBuffer

    def globalBuffer_=(value: Double) = network.globalBuffer = value

    def globalBufferSize = network.globalBufferSize

    def globalBufferSize_=(value: Double) = network.globalBufferSize = value

    def addConnector(connector: MutableConnector) = network.addConnector(connector)

    def removeConnector(connector: MutableConnector) = network.removeConnector(connector)

    def changeBuffer(delta: Double) = network.changeBuffer(delta)
  }

}