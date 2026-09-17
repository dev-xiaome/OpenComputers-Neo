package li.cil.oc.common.event

import li.cil.oc.api.event.RobotPlaceInAirEvent
import li.cil.oc.api.network.Node
import li.cil.oc.server.component.UpgradeAngel
import net.neoforged.neoforge.common.NeoForge

import scala.jdk.CollectionConverters._

/**
 * 天使升级：允许机器人在空中放置方块，前提是它的网络里能到达一个天使升级。
 *
 * 1.21.1 迁移要点：`@SubscribeEvent` → 显式 `addListener`（见 [[initialize]]）；
 * `Node#reachableNodes` 是 Java `Iterable`，需要 `.asScala`。
 */
object AngelUpgradeHandler {
  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: RobotPlaceInAirEvent) => onPlaceInAir(e))
  }

  def onPlaceInAir(e: RobotPlaceInAirEvent): Unit = {
    val machine = e.agent.machine
    // TODO(server.machine): 机器层移植前 `machine` 可能为 null，这里做空值保护。
    if (machine == null) return
    val machineNode = machine.node
    e.setAllowed(machineNode.reachableNodes.asScala.exists {
      case node: Node if node.canBeReachedFrom(machineNode) =>
        node.host.isInstanceOf[UpgradeAngel]
      case _ => false
    })
  }
}
