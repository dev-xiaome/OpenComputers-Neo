package li.cil.oc

import li.cil.oc.api.CreativeTab
import li.cil.oc.common.DataComponents
import li.cil.oc.common.init.Registry
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * OpenComputers Neo 主入口类。
 *
 * 原版是带 `@Mod` 注解的 Scala object，1.21.1 下改为**带构造参数的 Scala class**：
 * NeoForge 需要能反射实例化 mod 类，而 Scala object 的构造器不可用，
 * 因此这里用普通 class。
 *
 * 初始化顺序（与 1.7.10 的 `Proxy.preInit/init/postInit` 对应）：
 *  1. 构造期：注册各类 `DeferredRegister`、网络 payload、创造模式标签页 → [[li.cil.oc.common.Proxy.preInit]]（注册表事件之前）；
 *  2. `FMLCommonSetupEvent`：**先**加载 Settings，**再** [[li.cil.oc.common.Proxy.init]]（依赖配置值）+ 处理 IMC；
 *  3. `FMLLoadCompleteEvent`：[[li.cil.oc.common.Proxy.postInit]]。
 */
@Mod("opencomputers_neo")
class OpenComputersNeo(modBus: IEventBus, container: ModContainer) {
  OpenComputersNeo.log.info("Greetings, user! Booting OpenComputers Neo.")

  // 配置必须**最先**加载：注册期（`RegisterEvent`）就会读 `Settings` 里的数值
  // （例如全息投影亮度、软盘容量），晚于注册会抛 `Settings.get()` 为 null。
  OpenComputers.loadSettings(FMLPaths.CONFIGDIR.get().resolve("opencomputers_neo.conf").toFile)

  DataComponents.REGISTRY.register(modBus)
  CreativeTab.register(modBus)
  li.cil.oc.common.SoundEvents.REGISTRY.register(modBus)

  // 注册层：方块 / 物品 / 方块实体 / 菜单的 DeferredRegister 全部在这里挂上事件总线，
  // 同时接线创造模式标签页（BuildCreativeModeTabContentsEvent）与方块实体合法方块
  // （BlockEntityTypeAddBlocksEvent）。必须在 mod 构造期完成。
  Registry.init(modBus)

  // 网络传输层：把 `opencomputers_neo:packet` 这个 payload 注册到 mod 事件总线，
  // 并装配客户端/服务端两个分发器。必须在 mod 构造期调用一次。
  li.cil.oc.common.PacketHandler.initialize(modBus)

  // 具体物品 / 方块 / API 对象接线（必须在注册表事件之前）。
  private val proxy = new li.cil.oc.common.Proxy
  proxy.preInit()

  // 战利品磁盘需要世界加载事件来读取存档目录里的自定义磁盘。
  NeoForge.EVENT_BUS.register(li.cil.oc.common.Loot)

  modBus.addListener(new java.util.function.Consumer[FMLCommonSetupEvent] {
    override def accept(event: FMLCommonSetupEvent): Unit = event.enqueueWork(new Runnable {
      override def run(): Unit = {
        // 配置已在构造期加载；这里做依赖配置值的初始化。
        proxy.init()
        // OC 自身的装配机/拆解机模板通过 IMC 注册，必须在各 mod 发完消息之后处理。
        li.cil.oc.common.IMC.processMessages()
      }
    })
  })

  modBus.addListener(new java.util.function.Consumer[FMLLoadCompleteEvent] {
    override def accept(event: FMLLoadCompleteEvent): Unit = event.enqueueWork(new Runnable {
      override def run(): Unit = proxy.postInit()
    })
  })

  modBus.addListener(new java.util.function.Consumer[FMLClientSetupEvent] {
    override def accept(event: FMLClientSetupEvent): Unit = event.enqueueWork(new Runnable {
      override def run(): Unit = {
        // 客户端初始化（方块实体渲染器、菜单、按键绑定等）将在后续阶段接入。
        // 临时启动自检：仅当游戏目录存在 `oc-selfcheck.on` 时启用（见 BootSelfCheck，
        // 调试完成后连同该文件一起删除）。
        li.cil.oc.common.init.BootSelfCheck.register()
      }
    })
  })
}

object OpenComputersNeo {
  final val MODID = "opencomputers_neo"
  final val NAME = "OpenComputers Neo"
  final val VERSION = "1.0.0"

  val log: Logger = LogManager.getLogger(NAME)
}
