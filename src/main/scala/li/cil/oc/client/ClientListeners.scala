package li.cil.oc.client

import li.cil.oc.client.renderer.PetRenderer
import li.cil.oc.common.{ClientListenerRegistrar, EventHandler, Loot}
import net.neoforged.neoforge.client.event.{ClientPlayerNetworkEvent, ClientTickEvent}
import net.neoforged.neoforge.common.NeoForge

/**
 * 客户端专属事件监听器的统一注册入口。
 *
 * ==它解决的编译期问题==
 * 1.7.10 里这些监听器分散在 `common.EventHandler.Client` /
 * `common.event.NanomachinesHandler.Client` / `common.event.RackMountableRenderHandler` 与
 * `client.ComponentTracker` 里，由 `common.event.EventHandlers` 统一注册。
 * 1.21.1 的增量编译集要求 `common` 包**不能**引用 `client` 包（否则整个 `client` 会被拖进
 * 编译集），因此这里把「注册客户端监听器」这件事整体收进 `client` 包，由
 * [[li.cil.oc.common.ClientHooks]] 按类名在运行期软引用调用（见该对象的说明）。
 *
 * 注册内容（与原 `EventHandlers.initialize` / `EventHandler.initialize` 一一对应）：
 *  | 原位置 | 现位置 |
 *  | --- | --- |
 *  | `common.EventHandler.Client`（客户端 tick、登录清理） | 本对象 |
 *  | `client.ComponentTracker.initialize()` | 本对象 |
 *  | `common.event.NanomachinesHandler.Client`（HUD） | [[NanomachineHud]] |
 *  | `common.event.RackMountableRenderHandler`（机架覆盖层） | [[RackMountableRenderHandler]] |
 *
 * 服务端侧的监听器仍然由 [[li.cil.oc.common.event.EventHandlers]] 注册，两者互不影响。
 */
object ClientListeners extends ClientListenerRegistrar {
  override def initialize(): Unit = {
    // 1) 客户端 tick 队列（`scheduleClient` 排进来的动作）与登录清理。
    NeoForge.EVENT_BUS.addListener((e: ClientTickEvent.Pre) => EventHandler.onClientTick())
    NeoForge.EVENT_BUS.addListener((e: ClientPlayerNetworkEvent.LoggingIn) => onClientLoggedIn())

    // 2) 客户端组件跟踪表（`client.ComponentTracker` 继承自 `common.ComponentTracker`）。
    ComponentTracker.initialize()

    // 3) 纳米机器 HUD 电量条。
    NanomachineHud.initialize()

    // 4) 机架挂载物的动态覆盖层渲染。
    RackMountableRenderHandler.initialize()
  }

  /**
   * 客户端连上服务器（含单人游戏进世界）时的清理。
   *
   * 对应 1.7.10 `common.EventHandler.Client#clientLoggedIn`：
   * 重置宠物（机器人 / 无人机）渲染器的初始化标记与隐藏名单，清空客户端战利品磁盘缓存，
   * 并启动 / 停止一次「计算机运行」的音效循环（用来结束可能残留的上一局循环音）。
   */
  private def onClientLoggedIn(): Unit = {
    PetRenderer.isInitialized = false
    PetRenderer.hidden.clear()
    Loot.disksForClient.clear()
    Loot.disksForCyclingClient.clear()

    Sound.startLoop(null, "computer_running", 0f, 0)
    EventHandler.scheduleServer(() => Sound.stopLoop(null))
  }
}
