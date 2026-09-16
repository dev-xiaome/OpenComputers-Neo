package li.cil.oc.common.event

import li.cil.oc.common.EventHandler
import li.cil.oc.common.SaveHandler
import net.neoforged.bus.api.IEventBus

/**
 * 事件层统一注册入口。
 *
 * 为什么不直接用注解：NeoForge 对 Scala `object` 上的 `@EventBusSubscriber` /
 * `@SubscribeEvent` 扫描并不可靠（Scala 会为 object 生成静态转发方法，注解位置与
 * 类扫描器的期望不一致，一旦没被扫到就是**静默失效**）。因此本工程统一改为在
 * 各处理器的 `initialize()` 里显式 `NeoForge.EVENT_BUS.addListener`，
 * 再由本对象汇总成一个入口，供主类在 mod 构造期调用一次。
 *
 * ==客户端专属部分为什么走软引用==
 * 1.7.10 里本对象还会直接注册四个客户端处理器（`client.ComponentTracker`、
 * `common.EventHandler.Client`、`NanomachinesHandler.Client`、`RackMountableRenderHandler`）。
 * 1.21.1 的增量编译集（`gradle.properties` 的 `scala_ported_packages`）不允许 `common`
 * 出现对 `client` 包的**编译期**引用 —— 只要有一处，整个 `client` 包就会被拖进编译集。
 * 因此那些处理器已整体搬进 `li.cil.oc.client`，由 `li.cil.oc.client.ClientListeners`
 * 统一注册，这里只通过 [[li.cil.oc.common.ClientHooks]] 在运行期（且仅物理客户端）
 * 按类名软引用调用一次。
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

    // 客户端专属：client.ComponentTracker、纳米机器 HUD、机架覆盖层渲染、
    // 客户端 tick / 登录清理。服务端上是空操作，失败只记日志。
    li.cil.oc.common.ClientHooks.initializeClientListeners()

    // common/event 下的各处理器
    AngelUpgradeHandler.initialize()
    BlockChangeHandler.initialize()
    ChunkloaderUpgradeHandler.initialize(modBus)
    ExperienceUpgradeHandler.initialize()
    FileSystemAccessHandler.initialize()
    HoverBootsHandler.initialize()
    NanomachinesHandler.initialize()
    NetworkActivityHandler.initialize()
    RobotCommonHandler.initialize()
    WirelessNetworkCardHandler.initialize()
  }
}
