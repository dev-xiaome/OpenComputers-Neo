package li.cil.oc.common

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
import li.cil.oc.server.machine.luaj.LuaJLuaArchitecture
import net.minecraft.world.item.Item
import net.neoforged.bus.api.{IEventBus, SubscribeEvent}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.{IPayloadContext, IPayloadHandler}
import net.neoforged.neoforge.network.registration.PayloadRegistrar

import scala.jdk.CollectionConverters._
import net.neoforged.fml.ModLoadingContext
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent

class Proxy {
  // NeoForge 1.21.1 移除了 FMLJavaModLoadingContext，改从当前 ModContainer 取 mod 事件总线。
  protected val modBus: IEventBus = ModLoadingContext.get.getActiveContainer.getEventBus

  // 客户端 Proxy 实例同时被 OpenComputers 的 @Mod 构造器和 client.Proxy 自己的类体
  // 注册到 mod 事件总线上，注册事件会被投递两次；同一个 payload 类型重复注册会直接抛异常，
  // 所以这里做个幂等保护。
  private var payloadsRegistered = false

  // NeoForge 的事件总线**不允许**被注册对象的父类带 `@SubscribeEvent` 方法
  // （`common.Proxy` 正是 `client.Proxy` 的父类，子类类体里的 `modBus.register(this)`
  // 会直接抛 IllegalArgumentException），所以这两个监听器改为在构造期显式注册。
  modBus.addListener((event: RegisterPayloadHandlersEvent) => registerPacket(event))
  modBus.addListener((event: FMLLoadCompleteEvent) => postInit(event))

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

    // 1.21.1：原生 Lua 架构（`server/machine/luac` 里的 `LuaStateFactory` 与
    // `NativeLua52/53/54Architecture`）已整体移出编译集（见 `src/main/scala-pending`），
    // 本版只剩 LuaJ 一种架构，因此不再按可用性逐个注册，也不再需要「回退到 LuaJ」的告警。
    api.Machine.add(classOf[LuaJLuaArchitecture])

    api.Machine.LuaArchitecture =
      if (Settings.get.forceLuaJ) classOf[LuaJLuaArchitecture]
      else api.Machine.architectures.asScala.head
  }

  def init(e: FMLCommonSetupEvent): Unit = {
    e.enqueueWork((() => {
      // 网络层不再需要在这里建通道：包体注册走 RegisterPayloadHandlersEvent，
      // 见本类的 onRegisterPayloads。
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

  /**
   * 注册唯一的网络包体。
   *
   * 沿用旧版「一个消息类型装 byte[]」的模型：整包内容仍是「压缩标志 + PacketType.id + 数据」，
   * 所以 PacketType 与 PacketBuilder 那套抽象以及所有收发调用点都保持原样。
   */
  def registerPacket(event: RegisterPayloadHandlersEvent): Unit = {
    if (payloadsRegistered) return
    payloadsRegistered = true

    // registerPayloadHandlersEvent.registrar 的参数是网络版本号（不是命名空间）；
    // 双向包必须用 playBidirectional：同一个 payload id 在同一 protocol 下重复注册会直接抛异常。
    val registrar: PayloadRegistrar = event.registrar("1")
    val handler: IPayloadHandler[PacketPayload] = new IPayloadHandler[PacketPayload] {
      override def handle(payload: PacketPayload, context: IPayloadContext): Unit = {
        // flow().isClientbound() 为真表示这是服务端发到客户端、在客户端被收到的包。
        context.enqueueWork(new Runnable {
          override def run(): Unit =
            CommonPacketHandler.handlePacket(context.flow().isClientbound(), payload.data, context.player())
        })
      }
    }
    registrar.playBidirectional[PacketPayload](PacketPayload.TYPE, PacketPayload.STREAM_CODEC, handler)
  }

  def onRegisterPayloads(event: RegisterPayloadHandlersEvent): Unit = registerPacket(event)

  def postInit(e: FMLLoadCompleteEvent): Unit = {
    // Don't allow driver registration after this point, to avoid issues.
    driver.Registry.locked = true
  }

  def registerModel(instance: Item, id: String): Unit = {}

  def registerModel(instance: Block, id: String): Unit = {}
}
