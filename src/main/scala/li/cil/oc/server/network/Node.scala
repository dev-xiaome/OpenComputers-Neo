package li.cil.oc.server.network

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.{Node => ImmutableNode}
import net.minecraft.nbt.CompoundTag

import scala.jdk.CollectionConverters._

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
    isInSameNetwork(other) && network.neighbors(this).asScala.exists(_ == other)

  // 返回类型收紧为 Scala 的 `Iterable`（它本身实现了 `java.lang.Iterable`，属于合法的
  // 协变收窄）。注意本文件 import 了 `li.cil.oc.api.network._`，其中的 `Iterable`
  // 会把 `scala.Iterable` 屏蔽掉，所以这里一律写全限定名。
  override def reachableNodes: scala.collection.Iterable[ImmutableNode] =
    if (network == null) scala.collection.Iterable.empty[ImmutableNode]
    else network.nodes(this).asScala

  override def neighbors: scala.collection.Iterable[ImmutableNode] =
    if (network == null) scala.collection.Iterable.empty[ImmutableNode]
    else network.neighbors(this).asScala

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