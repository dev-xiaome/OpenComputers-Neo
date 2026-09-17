package li.cil.oc.server

import li.cil.oc.api
import li.cil.oc.common.{Proxy => CommonProxy}
import li.cil.oc.integration.Mods
import li.cil.oc.server.machine.luaj.LuaJLuaArchitecture
import li.cil.oc.{OpenComputers, Settings}

/**
 * 服务端代理（对应 1.7.10 的 `li.cil.oc.server.Proxy`）。
 *
 * ==1.21.1 结构==
 * 1.7.10 里 `@SidedProxy` 会在客户端挑 `client.Proxy`、在服务端挑 `server.Proxy`，
 * 共同继承 [[li.cil.oc.common.Proxy]]（`preInit` / `init` / `postInit` 三段）。
 * 1.21.1 没有 `@SidedProxy`，主类 [[li.cil.oc.OpenComputersNeo]] 已显式写死
 * `private val proxy = new li.cil.oc.server.Proxy`，并在构造期 / `FMLCommonSetupEvent` /
 * `FMLLoadCompleteEvent` 里依次调用 `preInit` / `init` / `postInit`。
 * 两侧都使用本类（客户端同样需要驱动表与 API 接线），客户端专有的接线另行处理。
 *
 * 本类在 [[li.cil.oc.common.Proxy]] 的三段之外补齐**服务端专有**的接线：
 *  - `preInit`：把 API 的 driver / machine / network / nanomachines 指向 `server.*` 的实现，
 *    并注册 Lua 架构（`common.Proxy` 出于「编译期不能引用 server 包」的限制只留了 TODO）；
 *  - `init`：初始化模组集成（[[Mods.init]]，进而注册 OC 自身与 Vanilla 的驱动）、
 *    注册服务端网络包处理器、挂上服务端组件跟踪表的世界卸载监听；
 *  - `postInit`：锁死驱动注册表，之后不再允许注册驱动。
 *
 * ==降级说明==
 *  - 1.7.10 的 `NetworkRegistry.INSTANCE.registerGuiHandler(OpenComputers, GuiHandler)`
 *    在 1.21.1 由 `MenuType` + `MenuProvider` 取代（见 [[li.cil.oc.common.container.MenuOpening]]），
 *    因此 [[init]] 里不再需要这一步；[[GuiHandler]] 仅为兼容旧调用链保留。
 *  - 原生 Lua（`machine.luac`）按 `docs/PROGRESS.md` 第 7 条不移植，只注册 LuaJ 架构。
 */
class Proxy extends CommonProxy {

  override def preInit(): Unit = {
    super.preInit()

    // `common.Proxy.preInit` 里这些赋值都被注释掉了（common 层不能引用 server 包）。
    // 服务端代理是它们的正确归宿。
    OpenComputers.log.debug("Wiring server-side API implementations.")
    api.API.driver = li.cil.oc.server.driver.Registry
    api.API.machine = li.cil.oc.server.machine.Machine
    api.API.network = li.cil.oc.server.network.Network
    api.API.nanomachines = li.cil.oc.common.nanomachines.Nanomachines

    // TODO(server.machine.luac): 原生 Lua（`machine.luac.LuaStateFactory` 及其 5.2 / 5.3 / 5.4
    //   架构）按 `docs/PROGRESS.md` 第 7 条暂不移植；恢复时在这里按 `LuaStateFactory.include5x`
    //   逐个 `api.Machine.add(...)`。
    api.Machine.add(classOf[LuaJLuaArchitecture])
    api.Machine.LuaArchitecture =
      if (Settings.get.forceLuaJ) classOf[LuaJLuaArchitecture]
      else api.Machine.architectures().iterator().next()
  }

  override def init(): Unit = {
    super.init()

    OpenComputers.log.debug("Initializing mod integration.")
    // 1.7.10 里 `Mods.init()` 位于 `common.Proxy.init`；1.21.1 的 `common` 层不能引用
    // `integration`（它在 `integration` 包里，且依赖 `server.*`），故上移到服务端代理。
    Mods.init()

    // 网络层：把「包类型 → 服务端处理方法」登记进 common 层的注册表。
    // payload 本身与两侧入口由主类调用 `common.PacketHandler.initialize(modBus)` 时装配。
    PacketHandler.initialize()

    // 服务端组件跟踪表：世界卸载时清空按维度缓存的组件。
    ComponentTracker.initialize()

    // 容器的「自定义数据」投递回调（1.7.10 是 `ServerPacketSender.sendContainerUpdate`）。
    // `common.container.Player` 不能引用 `server` 包，所以把回调留成了空操作，
    // 必须在这里接上 —— 否则进度条 / 机架节点映射 / 服务器运行状态之类
    // 只靠自定义 NBT 同步的 GUI 数据永远不会发给客户端。
    li.cil.oc.common.container.Player.customDataSync =
      (menu, nbt, player) => PacketSender.sendContainerUpdate(menu, nbt, player)
    OpenComputers.log.debug("Wired container custom data sync to the server packet sender.")

    // 服务端命令（1.7.10 在 `FMLServerStartingEvent` 里 `CommandHandler.register(e)`）：
    // 1.21.1 改为挂 `RegisterCommandsEvent` 监听，Brigadier 的 `CommandDispatcher`
    // 由 NeoForge 在开服时传进来。
    command.CommandHandler.initialize()
  }

  override def postInit(): Unit = {
    super.postInit()

    // 驱动注册到此为止：之后再注册会抛 `IllegalStateException`，避免运行中途出现新组件类型。
    li.cil.oc.server.driver.Registry.locked = true
    OpenComputers.log.debug("Locked the driver registry.")
  }
}
