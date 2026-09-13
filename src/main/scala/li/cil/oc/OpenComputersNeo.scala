package li.cil.oc

import li.cil.oc.api.CreativeTab
import li.cil.oc.common.DataComponents
import li.cil.oc.common.init.Registry
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLPaths
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * OpenComputers Neo 主入口类。
 *
 * 原版是带 `@Mod` 注解的 Scala object，1.21.1 下改为**带构造参数的 Scala class**：
 * NeoForge 需要能反射实例化 mod 类，而 Scala object 的构造器不可用，
 * 因此这里用普通 class。
 */
@Mod("opencomputers_neo")
class OpenComputersNeo(modBus: IEventBus, container: ModContainer) {
  OpenComputersNeo.log.info("Greetings, user! Booting OpenComputers Neo.")

  DataComponents.REGISTRY.register(modBus)
  CreativeTab.register(modBus)

  // 注册层：方块 / 物品 / 方块实体 / 菜单的 DeferredRegister 全部在这里挂上事件总线，
  // 同时接线创造模式标签页（BuildCreativeModeTabContentsEvent）与方块实体合法方块
  // （BlockEntityTypeAddBlocksEvent）。必须在 mod 构造期完成。
  Registry.init(modBus)

  modBus.addListener(new java.util.function.Consumer[FMLCommonSetupEvent] {
    override def accept(event: FMLCommonSetupEvent): Unit = event.enqueueWork(new Runnable {
      override def run(): Unit = {
        val configFile = FMLPaths.CONFIGDIR.get().resolve("opencomputers_neo.conf").toFile
        OpenComputers.loadSettings(configFile)
      }
    })
  })

  modBus.addListener(new java.util.function.Consumer[FMLClientSetupEvent] {
    override def accept(event: FMLClientSetupEvent): Unit = event.enqueueWork(new Runnable {
      override def run(): Unit = {
        // 客户端初始化（方块实体渲染器、菜单、按键绑定等）将在后续阶段接入。
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
