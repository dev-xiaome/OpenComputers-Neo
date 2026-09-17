package li.cil.oc.common.event

import li.cil.oc.api.event.NetworkActivityEvent
import li.cil.oc.api.internal.Rack
import li.cil.oc.common.tileentity.Case
import li.cil.oc.server.component.Server
import net.neoforged.neoforge.common.NeoForge

/**
 * 网络活动：在服务端刷新机架服务器的「最近网络活动」时间戳，在客户端刷新机箱的指示灯。
 *
 * 1.21.1 迁移要点：`@SubscribeEvent` → 显式 `addListener`（见 [[initialize]]）；
 * `Rack#getSizeInventory` → `IItemHandler#getSlots`。
 */
object NetworkActivityHandler {
  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: NetworkActivityEvent.Server) => onNetworkActivity(e))
    NeoForge.EVENT_BUS.addListener((e: NetworkActivityEvent.Client) => onNetworkActivity(e))
  }

  def onNetworkActivity(e: NetworkActivityEvent.Server): Unit = {
    e.getTileEntity match {
      case t: Rack =>
        for (slot <- 0 until t.getSlots) {
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

  def onNetworkActivity(e: NetworkActivityEvent.Client): Unit = {
    e.getTileEntity match {
      case t: Case => t.lastNetworkActivity = System.currentTimeMillis()
      case _ =>
    }
  }
}
