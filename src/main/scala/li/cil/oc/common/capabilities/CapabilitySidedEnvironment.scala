package li.cil.oc.common.capabilities

import li.cil.oc.api.network.{Node, SidedEnvironment}
import net.minecraft.core.Direction

// NeoForge 1.21.1: The old ICapabilityProvider/ICapabilitySerializable/LazyOptional system
// has been removed. Capabilities are now registered via RegisterCapabilitiesEvent.
// Block entities that implement SidedEnvironment are registered directly in EventHandler.
// This object is kept for the DefaultImpl helper class.
object CapabilitySidedEnvironment {

  class DefaultImpl extends SidedEnvironment {
    override def sidedNode(side: Direction): Node = null

    override def canConnect(side: Direction): Boolean = false
  }
}
