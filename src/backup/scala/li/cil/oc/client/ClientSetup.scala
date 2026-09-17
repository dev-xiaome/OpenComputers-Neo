package li.cil.oc.client

import net.neoforged.bus.api.IEventBus

/**
 * 客户端初始化入口（薄封装）。
 *
 * 1.7.10 的 `@SidedProxy(clientSide = "li.cil.oc.client.Proxy")` 在 1.21.1 不存在了，
 * 客户端接线必须由主类在 mod 构造期显式调用一次。本项目把它收敛成一个入口：
 *
 * {{{
 *   // li.cil.oc.OpenComputersNeo 的构造函数里（或 common/event/EventHandlers 里）
 *   if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient) {
 *     li.cil.oc.client.ClientSetup.initialize(modBus)
 *   }
 * }}}
 *
 * 本对象只做转发，真正的注册逻辑在 [[Proxy.initialize]]（避免两处维护同一份清单）。
 * 专用服务端上**不要**调用它：里面会引用 `net.minecraft.client.*` 下的类，
 * 在专用服务端会 `NoClassDefFoundError`。
 */
object ClientSetup {
  /** 客户端事件接线；`modBus` 是 mod 事件总线（`@Mod` 构造器的第一个参数）。 */
  def initialize(modBus: IEventBus): Unit = Proxy.initialize(modBus)
}
