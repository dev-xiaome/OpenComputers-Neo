package li.cil.oc.common.event

import net.neoforged.bus.api.SubscribeEvent
import li.cil.oc.api
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.server.component.WirelessNetworkCard

import scala.jdk.CollectionConverters._

object WirelessNetworkCardHandler {
  @SubscribeEvent
  def onMove(e: RobotMoveEvent.Post): Unit = {
    val machineNode = e.agent.machine.node
    machineNode.reachableNodes.foreach(_.host match {
      case card: WirelessNetworkCard => api.Network.updateWirelessNetwork(card)
      case _ =>
    })
  }
}
