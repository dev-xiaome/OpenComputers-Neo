package li.cil.oc

import li.cil.oc.client.ColorHandler
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.common.condition.ContentConditions
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.entity.EntityTypes
import li.cil.oc.common.init.{OCBlocks, OCItems}
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.VillageContent
import li.cil.oc.common.openprinter.OpenPrinter
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.common.{IMC, Proxy}
import li.cil.oc.integration.Mods
import li.cil.oc.server.command.CommandHandler
import li.cil.oc.server.loot.{LootConditions, LootFunctions}
import li.cil.oc.server.loot.LootTableHandler
import li.cil.oc.util.ThreadPoolFactory
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.{IEventBus, SubscribeEvent}
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.{FMLCommonSetupEvent, InterModProcessEvent}
import net.neoforged.fml.loading.{FMLLoader, FMLPaths}
import net.neoforged.fml.{InterModComms, ModContainer}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforgespi.Environment
import org.apache.logging.log4j.{Logger, LogManager}

import java.nio.file.Paths
import scala.jdk.CollectionConverters._

object OpenComputersNeo {
  final val ID = "opencomputers_neo"

  final val Name = "OpenComputers Neo"

  // 版本标识不硬编码：mod 版本来自 mods.toml（构建时由 gradle.properties 的
  // mod_version 注入），MC 版本在运行时从加载器读取（来自构建配置的
  // minecraft_version），两者始终保持一致。
  def McVersion: String = s"${FMLLoader.versionInfo().mcVersion()}-neoforge"

  @volatile var Version = "unknown"

  final val log: Logger = LogManager.getLogger(Name)

  var proxy: Proxy = _

  private var instance: Option[OpenComputersNeo] = None

  def get = instance match {
    case Some(oc) => oc
    case _ => throw new IllegalStateException("not initialized")
  }
}

@Mod(OpenComputersNeo.ID)
class OpenComputersNeo(modBus: IEventBus, modContainer: ModContainer) {
  OpenComputersNeo.Version = modContainer.getModInfo.getVersion.toString

  OpenComputersNeo.proxy = {
    val cls = Environment.get.getDist match {
      case Dist.CLIENT => Class.forName("li.cil.oc.client.Proxy")
      case _ => Class.forName("li.cil.oc.common.ServerProxy")
    }
    cls.getConstructor(classOf[IEventBus]).newInstance(modBus).asInstanceOf[Proxy]
  }

  Settings.load(FMLPaths.CONFIGDIR.get().resolve(Paths.get("opencomputers", "settings.conf")).toFile())

  modBus.register(this)
  OCComponents.init(modBus)
  ContentConditions.init(modBus)
  OCItems.init(modBus)
  OCBlocks.init(modBus)
  CreativeTab.CREATIVE_TABS.register(modBus)
  BlockEntityTypes.init(modBus)
  Recipes.init(modBus)
  LootConditions.init(modBus)
  LootFunctions.init(modBus)
  VillageContent.init(modBus)
  EntityTypes.ENTITY_TYPES.register(modBus)
  modBus.addListener(EntityTypes.onAttributeCreation)
  MenuTypes.MENU.register(modBus)
  OpenPrinter.init(modBus, modContainer)
  modBus.register(CreativeTab)
  OpenComputersNeo.instance = Some(this)
  modBus.register(OpenComputersNeo.proxy)
  OpenComputersNeo.proxy.preInit()
  NeoForge.EVENT_BUS.register(ThreadPoolFactory)
  NeoForge.EVENT_BUS.register(LootTableHandler.INSTANCE)
  NeoForge.EVENT_BUS.register(VillageContent.INSTANCE)
  NeoForge.EVENT_BUS.addListener(CommandHandler.onRegisterCommands)
  modBus.register(ColorHandler)

  Mods.preInit() // Must happen after loading Settings but before registry events are fired.

  @SubscribeEvent
  def imc(e: InterModProcessEvent): Unit = {
    // Technically requires synchronization because IMC.sendTo doesn't check the loading stage.
    e.enqueueWork((() => {
      InterModComms.getMessages(OpenComputersNeo.ID).sequential.iterator().asScala.foreach(IMC.handleMessage)
    }): Runnable)
  }

  @SubscribeEvent
  def onCommonSetup(e: FMLCommonSetupEvent): Unit = {
    OpenComputersNeo.proxy.init(e)
  }
}
