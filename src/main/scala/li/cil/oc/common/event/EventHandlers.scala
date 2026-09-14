package li.cil.oc.common.event

import li.cil.oc.common.EventHandler
import li.cil.oc.common.SaveHandler
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment

/**
 * 事件层统一注册入口。
 *
 * 为什么不直接用注解：NeoForge 对 Scala `object` 上的 `@EventBusSubscriber` /
 * `@SubscribeEvent` 扫描并不可靠（Scala 会为 object 生成静态转发方法，注解位置与
 * 类扫描器的期望不一致，一旦没被扫到就是**静默失效**）。因此本工程统一改为在
 * 各处理器的 `initialize()` 里显式 `NeoForge.EVENT_BUS.addListener`，
 * 再由本对象汇总成一个入口，供主类在 mod 构造期调用一次。
 *
 * 客户端专用的处理器（客户端 tick、HUD、机架渲染）内部各自用
 * `FMLEnvironment.dist.isClient` 做了判定，专用服务端上不会被注册。
 *
 * 用法（主类里加一行即可）：
 * {{{
 *   li.cil.oc.common.event.EventHandlers.initialize(modBus)
 * }}}
 */
object EventHandlers {
  def initialize(modBus: IEventBus): Unit = {
    // 通用 / 服务端
    EventHandler.initialize()
    SaveHandler.initialize()
    li.cil.oc.server.ComponentTracker.initialize()

    // 客户端专用的组件跟踪表（client.ComponentTracker 继承自 common.ComponentTracker）
    if (FMLEnvironment.dist.isClient) {
      li.cil.oc.client.ComponentTracker.initialize()
    }

    // common/event 下的各处理器
    AngelUpgradeHandler.initialize()
    BlockChangeHandler.initialize()
    ChunkloaderUpgradeHandler.initialize(modBus)
    ExperienceUpgradeHandler.initialize()
    FileSystemAccessHandler.initialize()
    HoverBootsHandler.initialize()
    NanomachinesHandler.initialize()
    NetworkActivityHandler.initialize()
    RackMountableRenderHandler.initialize()
    RobotCommonHandler.initialize()
    WirelessNetworkCardHandler.initialize()
  }
}
