package li.cil.oc.common.event

import li.cil.oc.api.event.NetworkActivityEvent
import li.cil.oc.api.internal.Rack
import li.cil.oc.common.blockentity.Case
import li.cil.oc.server.component.Server
import net.neoforged.bus.api.SubscribeEvent

object NetworkActivityHandler {
  @SubscribeEvent
  def onNetworkActivity(e: NetworkActivityEvent.Server): Unit = {
    e.getBlockEntity match {
      case t: Rack =>
        for (slot <- 0 until t.getContainerSize) {
          t.getMountable(slot) match {
            case server: Server =>
              val containsNode = server.componentSlot(e.getNode.address) >= 0
              if (containsNode) {
                server.lastNetworkActivity = System.currentTimeMillis()
                t.markChanged(slot)
              }
            case _ =>
          }
        }
      case _ =>
    }
  }

  @SubscribeEvent
  def onNetworkActivity(e: NetworkActivityEvent.Client): Unit = {
    e.getBlockEntity match {
      case t: Case => t.lastNetworkActivity = System.currentTimeMillis();
      case _ =>
    }
  }
}
