package li.cil.oc.server.network

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.{Node => ImmutableNode}
import net.minecraft.nbt.CompoundTag

import scala.collection.mutable

trait Node extends ImmutableNode {
  def host: Environment
  def reachability: Visibility

  final var address: String = null

  final var network: api.network.Network = null

  def canBeReachedFrom(other: ImmutableNode) = reachability match {
    case Visibility.None => false
    case Visibility.Neighbors => isNeighborOf(other)
    case Visibility.Network => isInSameNetwork(other)
  }

  def isNeighborOf(other: ImmutableNode) =
    isInSameNetwork(other) && NodeCollections.toScala(network.neighbors(this)).exists(_ == other)

  // 保持 API 声明的返回类型不变（`api.network.Node#neighbors()` / `#reachableNodes()`
  // 的签名是裸 `Iterable`）。本包内部要按 Scala 集合使用这两个值时，统一走
  // [[NodeCollections.toScala]]（原因见该对象的注释）。
  override def reachableNodes: java.lang.Iterable[ImmutableNode] =
    if (network == null) java.util.Collections.emptyList[ImmutableNode]()
    else NodeCollections.toJavaCollection(NodeCollections.toScala(network.nodes(this)))

  override def neighbors: java.lang.Iterable[ImmutableNode] =
    if (network == null) java.util.Collections.emptyList[ImmutableNode]()
    else NodeCollections.toJavaCollection(NodeCollections.toScala(network.neighbors(this)))

  // A node should be added to a network before it can connect to a node
  // but, sometimes other mods try to create nodes and connect them before
  // the network is ready. We don't desire those things to crash here.
  // With typical nodes we are talking about components here
  // which will be connected anyways when the network is created
  override def connect(node: ImmutableNode): Unit = if (network != null) network.connect(this, node)

  override def disconnect(node: ImmutableNode) =
    if (network != null && isInSameNetwork(node)) network.disconnect(this, node)

  override def remove() = if (network != null) network.remove(this)

  private def isInSameNetwork(other: ImmutableNode) = network != null && other != null && network == other.network

  // ----------------------------------------------------------------------- //

  def onConnect(node: ImmutableNode): Unit = {
    try {
      host.onConnect(node)
    } catch {
      case e: Throwable => OpenComputers.log.warn(s"A component of type '${host.getClass.getName}' threw an error while being connected to the component network.", e)
    }
  }

  def onDisconnect(node: ImmutableNode): Unit = {
    try {
      host.onDisconnect(node)
    } catch {
      case e: Throwable => OpenComputers.log.warn(s"A component of type '${host.getClass.getName}' threw an error while being disconnected from the component network.", e)
    }
  }

  // ----------------------------------------------------------------------- //

  def load(nbt: CompoundTag) = {
    if (nbt.contains("address")) {
      val newAddress = nbt.getString("address")
      if (!Strings.isNullOrEmpty(newAddress) && newAddress != address) network match {
        case wrapper: Network.Wrapper => wrapper.network.remap(this, newAddress)
        case _ => address = newAddress
      }
    }
  }

  def save(nbt: CompoundTag) = {
    if (address != null) {
      nbt.putString("address", address)
    }
  }

  override def toString = s"Node($address, $host)"
}

/**
 * 网络查询结果的 Java/Scala 集合互转工具。
 *
 * 为什么不直接用 `scala.jdk.CollectionConverters` 的隐式转换：
 *  - 本包同时导入了 `li.cil.oc.api.network._`，其中的 `Iterable`（Java 接口）会把
 *    `scala.Iterable` 这个名字遮住，`.asScala` / `.asJava` 的隐式解析在这种环境下不稳定；
 *  - Scala 2.13 的 `scala.collection.Iterable` **并不**继承 `java.lang.Iterable`，
 *    所以两边不能直接互相赋值。
 *
 * 这两个方法只依赖 `iterator()` / `add`，因此网络层实际返回 Scala 集合或 Java 集合都能兼容。
 *
 * 注意：这个对象**不能**命名为 `Node`，否则会被 `api.network.Node`（以及本包的 `Node` trait）
 * 遮住，调用点无法解析。
 */
private[network] object NodeCollections {
  def toScala(nodes: java.lang.Iterable[ImmutableNode]): scala.collection.Iterable[ImmutableNode] = {
    val buffer = mutable.ArrayBuffer.empty[ImmutableNode]
    val it = nodes.iterator()
    while (it.hasNext) {
      buffer += it.next()
    }
    buffer
  }

  def toJavaCollection(nodes: scala.collection.Iterable[ImmutableNode]): java.util.Collection[ImmutableNode] = {
    val list = new java.util.ArrayList[ImmutableNode]()
    nodes.foreach(node => list.add(node))
    list
  }
}

// We have to mixin the vararg methods individually in the actual
// implementations of the different node variants (see Network class) because
// for some reason it fails compiling on Linux otherwise (no clue why).
trait NodeVarargPart extends ImmutableNode {
  def sendToAddress(target: String, name: String, data: AnyRef*) =
    if (network != null) network.sendToAddress(this, target, name, data.toSeq: _*)

  def sendToNeighbors(name: String, data: AnyRef*) =
    if (network != null) network.sendToNeighbors(this, name, data.toSeq: _*)

  def sendToReachable(name: String, data: AnyRef*) =
    if (network != null) network.sendToReachable(this, name, data.toSeq: _*)

  def sendToVisible(name: String, data: AnyRef*) =
    if (network != null) network.sendToVisible(this, name, data.toSeq: _*)
}
