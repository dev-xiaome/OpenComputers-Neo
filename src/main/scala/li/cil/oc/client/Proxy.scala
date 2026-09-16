package li.cil.oc.client

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.renderer.entity.DroneRenderer
import li.cil.oc.client.renderer.item.ItemRenderer
import li.cil.oc.client.renderer.tileentity._
import li.cil.oc.client.renderer.{HighlightRenderer, MFUTargetRenderer, PetRenderer, TextBufferRenderCache, WirelessNetworkDebugRenderer}
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.init.Registry
import li.cil.oc.common.{Proxy => CommonProxy}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.{EventPriority, IEventBus}
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.{EntityRenderersEvent, RegisterKeyMappingsEvent, RegisterMenuScreensEvent}
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * 客户端初始化编排（对应 1.7.10 的 `client.Proxy`）。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - 没有 `@SidedProxy` / `FMLPreInitializationEvent` / `FMLInitializationEvent`，
 *    也没有 `ClientRegistry` / `RenderingRegistry` / `MinecraftForgeClient`。全部改成
 *    NeoForge 事件：
 *    | 1.7.10 | 1.21.1 |
 *    | --- | --- |
 *    | `ClientRegistry.bindTileEntitySpecialRenderer` | `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer` |
 *    | `RenderingRegistry.registerEntityRenderingHandler` | `EntityRenderersEvent.RegisterRenderers#registerEntityRenderer` |
 *    | `MinecraftForgeClient.registerItemRenderer` | `RegisterClientExtensionsEvent#registerItem(IClientItemExtensions)` |
 *    | `ClientRegistry.registerKeyBinding` | `RegisterKeyMappingsEvent#register` |
 *    | `NetworkRegistry.registerGuiHandler` + `client.GuiHandler` | `RegisterMenuScreensEvent#register`（见 [[GuiHandler.registerScreens]]） |
 *    | `RenderingRegistry.registerBlockHandler` | 1.21.1 用烘焙模型（`assets/.../blockstates`），无需注册 |
 *    | `FMLCommonHandler.instance.bus`（客户端 tick 等） | `NeoForge.EVENT_BUS.addListener` |
 *  - `Settings.blockRenderId` / `GLContext.getCapabilities.OpenGL15`（全息渲染器回退分支）整体删除：
 *    1.21.1 的渲染管线都是着色器 + 顶点缓冲，不存在「OpenGL 1.5 才支持」的分支。
 *
 * ==接线入口（重要）==
 * 本对象**不会自己注册**，必须由主类显式调用一次：
 * {{{
 *   if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient) {
 *     li.cil.oc.client.Proxy.initialize(modBus)
 *   }
 * }}}
 * 建议的落点是 `li.cil.oc.common.event.EventHandlers.initialize(modBus)` 里
 * `client.ComponentTracker.initialize()` 旁边（那里已经有 `FMLEnvironment.dist.isClient` 判定）。
 */
object Proxy {
  private var initialized = false

  /** 客户端事件接线；`modBus` 是 mod 事件总线（`@Mod` 构造器的第一个参数）。 */
  def initialize(modBus: IEventBus): Unit = {
    if (initialized) return
    initialized = true

    // --------------------------------------------------------------------- //
    // mod 事件总线：注册期事件
    // --------------------------------------------------------------------- //

    // 菜单 -> 屏幕工厂（取代 1.7.10 的 `NetworkRegistry.registerGuiHandler(OpenComputers, GuiHandler)`）。
    modBus.addListener(EventPriority.NORMAL, false, classOf[RegisterMenuScreensEvent],
      new java.util.function.Consumer[RegisterMenuScreensEvent] {
        override def accept(event: RegisterMenuScreensEvent): Unit = GuiHandler.registerScreens(event)
      })

    // 方块实体 / 实体渲染器（取代 `ClientRegistry.bindTileEntitySpecialRenderer`）。
    modBus.addListener(EventPriority.NORMAL, false, classOf[EntityRenderersEvent.RegisterRenderers],
      new java.util.function.Consumer[EntityRenderersEvent.RegisterRenderers] {
        override def accept(event: EntityRenderersEvent.RegisterRenderers): Unit = registerRenderers(event)
      })

    // 无人机的模型层定义（1.21.1 的 `EntityModel` 需要 `LayerDefinition` 提前登记）。
    modBus.addListener(EventPriority.NORMAL, false, classOf[EntityRenderersEvent.RegisterLayerDefinitions],
      new java.util.function.Consumer[EntityRenderersEvent.RegisterLayerDefinitions] {
        override def accept(event: EntityRenderersEvent.RegisterLayerDefinitions): Unit = DroneRenderer.registerLayerDefinitions(event)
      })

    // 带自定义渲染器的物品（取代 `MinecraftForgeClient.registerItemRenderer`）。
    modBus.addListener(EventPriority.NORMAL, false, classOf[RegisterClientExtensionsEvent],
      new java.util.function.Consumer[RegisterClientExtensionsEvent] {
        override def accept(event: RegisterClientExtensionsEvent): Unit = ItemRenderer.registerExtensions(event)
      })

    // 键位（取代 `ClientRegistry.registerKeyBinding`）。
    modBus.addListener(EventPriority.NORMAL, false, classOf[RegisterKeyMappingsEvent],
      new java.util.function.Consumer[RegisterKeyMappingsEvent] {
        override def accept(event: RegisterKeyMappingsEvent): Unit = KeyBindings.register(event)
      })

    // 方块 / 物品染色（机箱分级色、屏幕、线缆、变色石）。
    // 注意必须传 **mod 总线**，且只能在客户端调用（ColorHandlers 引用了 net.minecraft.client.*）。
    ColorHandlers.initialize(modBus)

    // 客户端 setup：API 接线 + 各子系统的运行期监听器。
    modBus.addListener(EventPriority.NORMAL, false, classOf[FMLClientSetupEvent],
      new java.util.function.Consumer[FMLClientSetupEvent] {
        override def accept(event: FMLClientSetupEvent): Unit = event.enqueueWork(new Runnable {
          override def run(): Unit = clientSetup()
        })
      })

    // --------------------------------------------------------------------- //
    // 游戏事件总线：运行期事件
    // --------------------------------------------------------------------- //

    // 生命周期的公共部分（世界卸载等）由 common 侧处理；这里只补客户端专有的。
    ClientEvents.initialize()
  }

  // ----------------------------------------------------------------------- //
  // 各项注册
  // ----------------------------------------------------------------------- //

  /**
   * 注册方块实体渲染器与实体渲染器。
   *
   * 1.7.10 是按**方块实体类**绑定（`classOf[tileentity.X]`），1.21.1 必须按
   * [[net.minecraft.world.level.block.entity.BlockEntityType]] 绑定，而类型在
   * [[li.cil.oc.common.init.Registry]] 里按「方块名（小写）」登记，因此这里用
   * `Constants.BlockName` 常量去查。
   *
   * 同一份渲染器会被多个等级方块共用（例如 `CaseTier1/2/3/CaseCreative` 都是 [[tileentity.Case]]，
   * `ScreenTier1/2/3` 都是 [[tileentity.Screen]]，`AccessPoint`/`Relay`/`Switch` 共用
   * [[SwitchRenderer]]），这与 1.7.10 的语义一致。
   */
  private def registerRenderers(event: EntityRenderersEvent.RegisterRenderers): Unit = {
    import li.cil.oc.Constants.BlockName

    registerBlockEntityRenderer(event, BlockName.AccessPoint, () => new SwitchRenderer)
    registerBlockEntityRenderer(event, BlockName.Adapter, () => new AdapterRenderer)
    registerBlockEntityRenderer(event, BlockName.Assembler, () => new AssemblerRenderer)
    registerBlockEntityRenderer(event, BlockName.CaseTier1, () => new CaseRenderer)
    registerBlockEntityRenderer(event, BlockName.CaseTier2, () => new CaseRenderer)
    registerBlockEntityRenderer(event, BlockName.CaseTier3, () => new CaseRenderer)
    registerBlockEntityRenderer(event, BlockName.CaseCreative, () => new CaseRenderer)
    registerBlockEntityRenderer(event, BlockName.Charger, () => new ChargerRenderer)
    registerBlockEntityRenderer(event, BlockName.Disassembler, () => new DisassemblerRenderer)
    registerBlockEntityRenderer(event, BlockName.DiskDrive, () => new DiskDriveRenderer)
    registerBlockEntityRenderer(event, BlockName.Geolyzer, () => new GeolyzerRenderer)
    // 1.7.10 里按 `GLContext.getCapabilities.OpenGL15` 在 HologramRenderer / Fallback 之间二选一；
    // 1.21.1 的渲染管线统一是着色器 + 顶点缓冲，恒用完整版。
    registerBlockEntityRenderer(event, BlockName.HologramTier1, () => new HologramRenderer)
    registerBlockEntityRenderer(event, BlockName.HologramTier2, () => new HologramRenderer)
    registerBlockEntityRenderer(event, BlockName.Microcontroller, () => new MicrocontrollerRenderer)
    registerBlockEntityRenderer(event, BlockName.NetSplitter, () => new NetSplitterRenderer)
    registerBlockEntityRenderer(event, BlockName.PowerDistributor, () => new PowerDistributorRenderer)
    registerBlockEntityRenderer(event, BlockName.Printer, () => new PrinterRenderer)
    registerBlockEntityRenderer(event, BlockName.Raid, () => new RaidRenderer)
    registerBlockEntityRenderer(event, BlockName.Rack, () => new RackRenderer)
    registerBlockEntityRenderer(event, BlockName.Relay, () => new SwitchRenderer)
    registerBlockEntityRenderer(event, BlockName.ScreenTier1, () => new ScreenRenderer)
    registerBlockEntityRenderer(event, BlockName.ScreenTier2, () => new ScreenRenderer)
    registerBlockEntityRenderer(event, BlockName.ScreenTier3, () => new ScreenRenderer)
    registerBlockEntityRenderer(event, BlockName.Switch, () => new SwitchRenderer)
    registerBlockEntityRenderer(event, BlockName.Transposer, () => new TransposerRenderer)
    // 机器人本体是方块实体（`RobotProxy` 里内嵌 `entity.Robot`）。
    registerBlockEntityRenderer(event, BlockName.Robot, () => new RobotRenderer)

    // 无人机实体（原 `RenderingRegistry.registerEntityRenderingHandler(classOf[Drone], DroneRenderer)`）。
    val droneType = Registry.droneType
    if (droneType != null) {
      event.registerEntityRenderer(
        droneType.asInstanceOf[net.minecraft.world.entity.EntityType[Drone]],
        new EntityRendererProvider[Drone] {
          override def create(context: EntityRendererProvider.Context) = new DroneRenderer(context)
        })
    }
  }

  /**
   * 按方块名查出 [[net.minecraft.world.level.block.entity.BlockEntityType]] 并绑定渲染器。
   *
   * `Registry.getBlockEntityType` 返回的是 `BlockEntityType[_]`（`DeferredHolder` 的第二类型参数被擦除），
   * 因此这里必须强制转型；转型是安全的，因为类型名与方块名一一对应（见 `Registry.initBlocks`）。
   */
  private def registerBlockEntityRenderer[T <: BlockEntity](event: EntityRenderersEvent.RegisterRenderers,
                                                            blockName: String,
                                                            factory: () => net.minecraft.client.renderer.blockentity.BlockEntityRenderer[T]): Unit = {
    val blockEntityType = Registry.getBlockEntityType(blockName)
    if (blockEntityType == null) return
    event.registerBlockEntityRenderer(
      blockEntityType.asInstanceOf[net.minecraft.world.level.block.entity.BlockEntityType[T]],
      new BlockEntityRendererProvider[T] {
        override def create(context: BlockEntityRendererProvider.Context) = factory()
      })
  }

  /** 客户端 setup：API 接线 + 各子系统的运行期监听器。 */
  private def clientSetup(): Unit = {
    // 手册实现（原 `api.API.manual = client.Manual`）。
    // 注意：本对象就在 `package li.cil.oc.client` 内，`client.Manual` 里的 `client`
    // 会被解析成 `li.cil.oc.client.client`，所以必须直接写 `Manual`。
    api.API.manual = Manual

    CommandHandler.initialize()
    Icons.initialize()
    Sound.initialize()
    PacketHandler.initialize()
    TextBufferRenderCache.initialize()

    HighlightRenderer.initialize()
    MFUTargetRenderer.initialize()
    PetRenderer.initialize()
    WirelessNetworkDebugRenderer.initialize()

    // 原 `Settings.blockRenderId = RenderingRegistry.getNextAvailableRenderId`：
    // 1.21.1 不再有「方块渲染 id」这个概念（改用烘焙模型 + `BlockEntityRenderer`），
    // `Settings.blockRenderId` 若仍被引用则保持默认值即可。
    val _ = Settings.get
  }
}

/**
 * 客户端专有的运行期事件接线。
 *
 * 1.7.10 的 `client.Proxy#init` 把 `HighlightRenderer` / `NanomachinesHandler.Client` /
 * `PetRenderer` / `RackMountableRenderHandler` / `Sound` / `TextBuffer` /
 * `MFUTargetRenderer` / `WirelessNetworkDebugRenderer` 注册到 `MinecraftForge.EVENT_BUS`，
 * 把 `Audio` / `HologramRenderer` / `PetRenderer` / `Sound` / `TextBufferRenderCache`
 * 注册到 `FMLCommonHandler.instance.bus`。
 *
 * 1.21.1 里两条总线合并成 `NeoForge.EVENT_BUS`，且本工程统一不用注解扫描
 * （原因见 `common/event/EventHandlers` 的说明），各处理器自己在 `initialize()` 里
 * `NeoForge.EVENT_BUS.addListener(...)`。
 */
private[oc] object ClientEvents {
  def initialize(): Unit = {
    // 这里保留一个集中的入口，方便以后统一管理；目前各子系统在 `Proxy.clientSetup`
    // 里自行注册，避免重复。
  }
}

/**
 * 1.7.10 的 `client.Proxy` 是一个 `class`（由 `@SidedProxy` 在客户端实例化）。
 * 1.21.1 没有 `@SidedProxy`，[Proxy] 改成 object 并由主类显式调用 [[Proxy.initialize]]。
 *
 * 这里保留同名的 class 只是为了让「按 1.7.10 结构检索代码」的人能找到对应物，
 * 它**不参与任何初始化**，也不应被实例化。
 */
@deprecated("1.21.1 请使用 li.cil.oc.client.Proxy.initialize(modBus)", "port")
private[oc] class Proxy extends CommonProxy
