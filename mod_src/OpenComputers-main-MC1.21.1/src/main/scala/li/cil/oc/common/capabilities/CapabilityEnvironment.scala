package li.cil.oc.common.capabilities

import li.cil.oc.api
import li.cil.oc.api.network.{Environment, Message, Node, Visibility}

// NeoForge 1.21.1: The old ICapabilityProvider/ICapabilitySerializable/LazyOptional system
// has been removed. Capabilities are now registered via RegisterCapabilitiesEvent.
// Block entities that implement Environment are registered directly in EventHandler
// via event.registerBlockEntity(...).
// This object is kept for the DefaultImpl helper class.
object CapabilityEnvironment {

  class DefaultImpl extends Environment {
    override val node: Node = api.Network.newNode(this, Visibility.None).create()

    override def onMessage(message: Message): Unit = {}

    override def onConnect(node: Node): Unit = {}

    override def onDisconnect(node: Node): Unit = {}
  }
}
