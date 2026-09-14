package li.cil.oc.common.event

import li.cil.oc.api
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.server.component.WirelessNetworkCard
import net.neoforged.neoforge.common.NeoForge

import scala.jdk.CollectionConverters._

/**
 * 无线网卡：机器人移动后刷新它所在的无线网络。
 *
 * 1.21.1 迁移要点：`@SubscribeEvent` → 显式 `addListener`；
 * `Node#reachableNodes` 是 Java `Iterable`，需要 `.asScala`。
 */
object WirelessNetworkCardHandler {
  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: RobotMoveEvent.Post) => onMove(e))
  }

  def onMove(e: RobotMoveEvent.Post): Unit = {
    val machine = e.agent.machine
    // TODO(server.machine): 机器层移植前 `machine` 可能为 null，这里做空值保护。
    if (machine == null) return
    machine.node.reachableNodes.asScala.foreach(_.host match {
      case card: WirelessNetworkCard => api.Network.updateWirelessNetwork(card)
      case _ =>
    })
  }
}
