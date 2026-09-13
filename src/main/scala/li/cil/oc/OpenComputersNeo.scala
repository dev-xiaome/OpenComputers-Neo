package li.cil.oc

import li.cil.oc.api.CreativeTab
import li.cil.oc.common.DataComponents
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
@Mod("open_computers_neo")
class OpenComputersNeo(modBus: IEventBus, container: ModContainer) {
  OpenComputersNeo.log.info("Greetings, user! Booting OpenComputers Neo.")

  DataComponents.REGISTRY.register(modBus)
  CreativeTab.register(modBus)

  modBus.addListener((event: FMLCommonSetupEvent) => event.enqueueWork(() => {
    val configFile = FMLPaths.CONFIGDIR.get().resolve("open_computers_neo.conf").toFile
    OpenComputers.loadSettings(configFile)
  }))

  modBus.addListener((event: FMLClientSetupEvent) => event.enqueueWork(() => {
    // 客户端初始化（方块实体渲染器、菜单、按键绑定等）将在后续阶段接入。
  }))
}

object OpenComputersNeo {
  final val MODID = "open_computers_neo"
  final val NAME = "OpenComputers Neo"
  final val VERSION = "1.0.0"

  val log: Logger = LogManager.getLogger(NAME)
}
