package li.cil.oc.server.network

import com.google.common.base.Strings
import li.cil.oc.{OpenComputers, api}
import li.cil.oc.api.network.{Environment, Visibility, Node => ImmutableNode}
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.DataComponentHolder
import net.neoforged.neoforge.common.MutableDataComponentHolder

import scala.collection.convert.ImplicitConversionsToJava._
import scala.collection.convert.ImplicitConversionsToScala._

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
    isInSameNetwork(other) && network.neighbors(this).exists(_ == other)

  def reachableNodes: java.lang.Iterable[ImmutableNode] =
    if (network == null) Iterable.empty[ImmutableNode].toSeq
    else network.nodes(this)

  def neighbors: java.lang.Iterable[ImmutableNode] =
    if (network == null) Iterable.empty[ImmutableNode].toSeq
    else network.neighbors(this)

  // A node should be added to a network before it can connect to a node
  // but, sometimes other mods try to create nodes and connect them before
  // the network is ready. We don't desire those things to crash here.
  // With typical nodes we are talking about components here
  // which will be connected anyways when the network is created
  def connect(node: ImmutableNode): Unit = if (network != null) network.connect(this, node)

  def disconnect(node: ImmutableNode) =
    if (network != null && isInSameNetwork(node)) network.disconnect(this, node)

  def remove() = if (network != null) network.remove(this)

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

  private[oc] def loadAddress(newAddress: String): Unit = {
    if (!Strings.isNullOrEmpty(newAddress) && newAddress != address) network match {
      case wrapper: Network.Wrapper => wrapper.network.remap(this, newAddress)
      case _ => address = newAddress
    }
  }

  override def loadData(holder: DataComponentHolder): Unit = {
    holder.getComponent(OCComponents.ADDRESS).foreach(loadAddress)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    if (address != null) {
      holder.setComponent(OCComponents.ADDRESS, address)
    }
  }

  override def toString = s"Node($address, $host)"
}

// We have to mixin the vararg methods individually in the actual
// implementations of the different node variants (see Network class) because
// for some reason it fails compiling on Linux otherwise (no clue why).
trait NodeVarargPart extends ImmutableNode {
  def sendToAddress(target: String, name: String, data: AnyRef*) =
    if (network != null) network.sendToAddress(this, target, name, data: _*)

  def sendToNeighbors(name: String, data: AnyRef*) =
    if (network != null) network.sendToNeighbors(this, name, data: _*)

  def sendToReachable(name: String, data: AnyRef*) =
    if (network != null) network.sendToReachable(this, name, data: _*)

  def sendToVisible(name: String, data: AnyRef*) =
    if (network != null) network.sendToVisible(this, name, data: _*)
}
