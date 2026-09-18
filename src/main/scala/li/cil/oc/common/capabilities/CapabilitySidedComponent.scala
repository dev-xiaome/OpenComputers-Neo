package li.cil.oc.common.capabilities

import li.cil.oc.api.network.{Environment, SidedComponent, SidedEnvironment}
import net.minecraft.core.Direction

// NeoForge 1.21.1: The old ICapabilityProvider/LazyOptional system has been removed.
// SidedComponent capabilities are now registered via RegisterCapabilitiesEvent
// in EventHandler using event.registerBlockEntity(...).
// The SidedEnvironment adapter logic is inlined in the registration lambda.
object CapabilitySidedComponent {

  /** Adapter that exposes a block entity implementing Environment+SidedComponent as SidedEnvironment. */
  class SidedEnvironmentAdapter(val env: Environment with SidedComponent) extends SidedEnvironment {
    override def sidedNode(side: Direction): li.cil.oc.api.network.Node =
      if (env.canConnectNode(side)) env.node else null

    override def canConnect(side: Direction): Boolean = env.canConnectNode(side)
  }
}
