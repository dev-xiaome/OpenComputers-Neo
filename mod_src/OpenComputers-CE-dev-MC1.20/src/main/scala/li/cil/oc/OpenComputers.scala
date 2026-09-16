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
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.forgespi.Environment
import net.minecraftforge.fml.InterModComms
import net.minecraftforge.fml.ModContainer
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.event.lifecycle.{FMLCommonSetupEvent, InterModProcessEvent}
import net.minecraftforge.fml.loading.FMLPaths
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import scala.collection.convert.ImplicitConversionsToScala._
import net.minecraftforge.network.simple.SimpleChannel
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.common.Mod

object OpenComputers {
  final val ID = "opencomputers"

  final val Name = "OpenComputers"

  final val McVersion = "1.20.1-forge"

  final val Version = "1.9.0-beta"

  final val log: Logger = LogManager.getLogger(Name)

  lazy val proxy: Proxy = {
    val cls = Environment.get.getDist match {
      case Dist.CLIENT => Class.forName("li.cil.oc.client.Proxy")
      case _ => Class.forName("li.cil.oc.common.Proxy")
    }
    cls.getConstructor().newInstance().asInstanceOf[Proxy]
  }

  var channel: SimpleChannel = null

  private var instance: Option[OpenComputers] = None

  def get = instance match {
    case Some(oc) => oc
    case _ => throw new IllegalStateException("not initialized")
  }
}

@Mod(OpenComputers.ID)
class OpenComputers {
  val modContainer: ModContainer = ModLoadingContext.get.getActiveContainer
  val modBus = FMLJavaModLoadingContext.get.getModEventBus

  modBus.register(this)
  Items.init(modBus)
  Blocks.init(modBus)
  CreativeTab.CREATIVE_TABS.register(modBus)
  BlockEntityTypes.init(modBus)
  Recipes.init(modBus)
  LootFunctions.init(modBus)
  EntityTypes.ENTITY_TYPES.register(modBus)
  MenuTypes.MENU_TYPES.register(modBus)
  modBus.register(classOf[Capabilities])
  modBus.register(li.cil.oc.data.DataGenerators)
  modBus.register(CreativeTab)
  OpenComputers.instance = Some(this)
  MinecraftForge.EVENT_BUS.register(OpenComputers.proxy)
  modBus.register(OpenComputers.proxy)
  Settings.load(FMLPaths.CONFIGDIR.get().resolve(Paths.get("opencomputers", "settings.conf")).toFile())
  OpenComputers.proxy.preInit()
  MinecraftForge.EVENT_BUS.register(ThreadPoolFactory)
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
