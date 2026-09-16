package li.cil.oc.common

import java.util.function.Supplier
import com.google.common.base.Strings
import li.cil.oc._
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.common.{PacketHandler => CommonPacketHandler}
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.entity.EntityTypes
import li.cil.oc.common.init.{Blocks, Items}
import li.cil.oc.common.item.RedstoneCard
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.integration.Mods
import li.cil.oc.integration.opencomputers.ModOpenComputers
import li.cil.oc.server
import li.cil.oc.server._
import li.cil.oc.server.loot.LootFunctions
import li.cil.oc.server.machine.luac.{LuaStateFactory, NativeLua52Architecture, NativeLua53Architecture, NativeLua54Architecture}
import li.cil.oc.server.machine.luaj.LuaJLuaArchitecture
import net.minecraft.world.item.Item
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.{IEventBus, SubscribeEvent}
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.registries.{ForgeRegistries, MissingMappingsEvent}

import scala.jdk.CollectionConverters._
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.block.Block
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent

object Proxy {
  // Yes, this could be boiled down even further, but I like to keep it
  // explicit like this, because it makes it a) clearer, b) easier to
  // extend, in case that should ever be needed.

  // Example usage: OpenComputers.ID + ":rack" -> "serverRack"
  private val blockRenames = Map[String, String](
    OpenComputers.ID + ":serverRack" -> Constants.BlockName.Rack // Yay, full circle >_>
  )

  // Example usage: OpenComputers.ID + ":tabletCase" -> "tabletCase1"
  private val itemRenames = Map[String, String](
    OpenComputers.ID + ":dataCard" -> Constants.ItemName.DataCardTier1,
    OpenComputers.ID + ":serverRack" -> Constants.BlockName.Rack,
    OpenComputers.ID + ":wlanCard" -> Constants.ItemName.WirelessNetworkCardTier2
  )

  @SubscribeEvent
  def onMissingMappings(e: MissingMappingsEvent): Unit = {
    e.getMappings(ForgeRegistries.Keys.BLOCKS, OpenComputers.ID).asScala.foreach { missing =>
      blockRenames.get(missing.getKey.getPath) match {
        case Some(name) =>
          if (Strings.isNullOrEmpty(name)) {
            missing.ignore()
          } else {
            val target = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, name))
            if (target != null) missing.remap(target) else missing.warn()
          }
        case _ => missing.warn()
      }
    }

    e.getMappings(ForgeRegistries.Keys.ITEMS, OpenComputers.ID).asScala.foreach { missing =>
      itemRenames.get(missing.getKey.getPath) match {
        case Some(name) =>
          if (Strings.isNullOrEmpty(name)) {
            missing.ignore()
          } else {
            val target = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, name))
            if (target != null) missing.remap(target) else missing.warn()
          }
        case _ => missing.warn()
      }
    }
  }
}

class Proxy {
  protected val modBus: IEventBus = FMLJavaModLoadingContext.get.getModEventBus

  def preInit(): Unit = {
    OpenComputers.log.info("Initializing OpenComputers API.")

    api.CreativeTab.instance = CreativeTab.MAIN.getKey
    api.API.driver = driver.Registry
    api.API.fileSystem = fs.FileSystem
    api.API.items = Items
    api.API.machine = machine.Machine
    api.API.nanomachines = nanomachines.Nanomachines
    api.API.network = network.Network
    api.API.audio = audio.Audio

    api.API.config = Settings.get.config

    if (LuaStateFactory.isAvailable) {
      if (LuaStateFactory.include53) {
        api.Machine.add(classOf[NativeLua53Architecture])
      }
      if (LuaStateFactory.include54) {
        api.Machine.add(classOf[NativeLua54Architecture])
      }
      if (LuaStateFactory.include52) {
        api.Machine.add(classOf[NativeLua52Architecture])
      }
    }
    if (LuaStateFactory.includeLuaJ) {
      api.Machine.add(classOf[LuaJLuaArchitecture])
    }

    api.Machine.LuaArchitecture =
      if (Settings.get.forceLuaJ) classOf[LuaJLuaArchitecture]
      else api.Machine.architectures.asScala.head
  }

  def init(e: FMLCommonSetupEvent): Unit = {
    e.enqueueWork((() => {
      OpenComputers.channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "net_main"), () => "", "".equals(_), "".equals(_))
      OpenComputers.channel.registerMessage(0, classOf[Array[Byte]],
        (msg: Array[Byte], buff: FriendlyByteBuf) => buff.writeByteArray(msg), _.readByteArray(),
        (msg: Array[Byte], ctx: Supplier[NetworkEvent.Context]) => {
          val context = ctx.get
          context.enqueueWork(() => CommonPacketHandler.handlePacket(context.getDirection, msg, context.getSender))
          context.setPacketHandled(true)
        })
      CommonPacketHandler.serverHandler = server.PacketHandler

      Loot.init()
      Achievement.init()

      OpenComputers.log.debug("Initializing mod integration.")
      Mods.init()

      OpenComputers.log.info("Initializing capabilities.")
      //Capabilities.init()
      //Capabilities are registered in event

      api.API.isPowerEnabled = !Settings.get.ignorePower
    }): Runnable)
  }

  @SubscribeEvent
  def postInit(e: FMLLoadCompleteEvent): Unit = {
    // Don't allow driver registration after this point, to avoid issues.
    driver.Registry.locked = true
  }

  def registerModel(instance: Item, id: String): Unit = {}

  def registerModel(instance: Block, id: String): Unit = {}
}