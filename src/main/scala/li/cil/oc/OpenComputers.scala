package li.cil.oc

import java.nio.file.Paths
import li.cil.oc.common.IMC
import li.cil.oc.common.Proxy
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.common.capabilities.Capabilities
import li.cil.oc.common.entity.EntityTypes
import li.cil.oc.common.init.Blocks
import li.cil.oc.common.init.Items
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.integration.Mods
import li.cil.oc.server.loot.LootFunctions
import li.cil.oc.util.ThreadPoolFactory
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.InterModComms
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.event.lifecycle.{FMLCommonSetupEvent, InterModProcessEvent}
import net.neoforged.fml.loading.FMLPaths
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import scala.collection.convert.ImplicitConversionsToScala._
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod

object OpenComputers {
  final val ID = "opencomputers_neo"

  final val Name = "OpenComputers"

  final val McVersion = "1.21.1-neoforge"

  final val Version = "1.9.0-beta"

  final val log: Logger = LogManager.getLogger(Name)

  lazy val proxy: Proxy = {
    val cls = net.neoforged.fml.loading.FMLEnvironment.dist match {
      case Dist.CLIENT => Class.forName("li.cil.oc.client.Proxy")
      case _ => Class.forName("li.cil.oc.common.Proxy")
    }
    cls.getConstructor().newInstance().asInstanceOf[Proxy]
  }

  private var instance: Option[OpenComputers] = None

  def get = instance match {
    case Some(oc) => oc
    case _ => throw new IllegalStateException("not initialized")
  }
}

@Mod(OpenComputers.ID)
class OpenComputers(modBus: IEventBus, container: ModContainer) {

  modBus.register(this)
  Items.init(modBus)
  Blocks.init(modBus)
  // 自定义数据组件 `opencomputers_neo:nbt`：**必须在这里注册**。
  // OC 的物品数据模型依赖一整棵可变的 CompoundTag 挂在物品上（`api.driver.Item#dataTag`），
  // NeoForge 1.21.1 的物品数据已改成数据组件，我们用这个组件承载它（见 common/DataComponents.java）。
  // 忘记注册的话，任何带数据的物品一被读写就会在运行期崩。
  li.cil.oc.common.DataComponents.REGISTRY.register(modBus)
  // NeoForge 的强制加载 ticket 控制器必须在 mod 事件总线上注册
  // （取代 Forge 1.20 在集成层调用的 `ForgeChunkManager.setForcedChunkLoadingCallback`）。
  common.event.ChunkloaderUpgradeHandler.initialize(modBus)
  CreativeTab.CREATIVE_TABS.register(modBus)
  BlockEntityTypes.init(modBus)
  Recipes.init(modBus)
  LootFunctions.init(modBus)
  EntityTypes.ENTITY_TYPES.register(modBus)
  MenuTypes.MENU_TYPES.register(modBus)
  modBus.register(Capabilities)
  modBus.register(li.cil.oc.data.DataGenerators)
  modBus.register(CreativeTab)
  OpenComputers.instance = Some(this)
  NeoForge.EVENT_BUS.register(OpenComputers.proxy)
  modBus.register(OpenComputers.proxy)
  Settings.load(FMLPaths.CONFIGDIR.get().resolve(Paths.get("opencomputers", "settings.conf")).toFile())
  OpenComputers.proxy.preInit()
  NeoForge.EVENT_BUS.register(ThreadPoolFactory)
  Mods.preInit() // Must happen after loading Settings but before registry events are fired.

  @SubscribeEvent
  def imc(e: InterModProcessEvent): Unit = {
    // Technically requires synchronization because IMC.sendTo doesn't check the loading stage.
    e.enqueueWork((() => {
      InterModComms.getMessages(OpenComputers.ID).sequential.iterator.foreach(IMC.handleMessage)
    }): Runnable)
  }

  @SubscribeEvent
  def onCommonSetup(e: FMLCommonSetupEvent): Unit = {
    OpenComputers.proxy.init(e)
  }
}
