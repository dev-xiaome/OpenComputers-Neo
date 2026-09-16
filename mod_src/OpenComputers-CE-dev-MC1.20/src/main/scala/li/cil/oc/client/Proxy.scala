package li.cil.oc.client

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.api
import li.cil.oc.client
import li.cil.oc.client.gui.GuiTypes
import li.cil.oc.client.renderer.HighlightRenderer
import li.cil.oc.client.renderer.MFUTargetRenderer
import li.cil.oc.client.renderer.PetRenderer
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.WirelessNetworkDebugRenderer
import li.cil.oc.client.renderer.block.ModelInitialization
import li.cil.oc.client.renderer.block.NetSplitterModel
import li.cil.oc.client.renderer.entity.{DroneRenderer, ModelQuadcopter}
import li.cil.oc.client.renderer.tileentity._
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.common.{PacketHandler => CommonPacketHandler}
import li.cil.oc.common.{Proxy => CommonProxy}
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.entity.EntityTypes
import li.cil.oc.common.event.NanomachinesHandler
import li.cil.oc.common.event.RackMountableRenderHandler
import li.cil.oc.util.Audio
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.Item
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent
import net.minecraftforge.client.event.{EntityRenderersEvent, RegisterKeyMappingsEvent}
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent

private[oc] class Proxy extends CommonProxy {
  modBus.register(this)
  modBus.register(classOf[GuiTypes])
  modBus.register(ModelInitialization)
  modBus.register(NetSplitterModel)
  modBus.register(Textures)

  override def preInit(): Unit = {
    super.preInit()

    api.API.manual = client.Manual
  }

  override def init(e: FMLCommonSetupEvent): Unit = {
    super.init(e)

    CommonPacketHandler.clientHandler = PacketHandler

    e.enqueueWork((() => {
      ModelInitialization.preInit()

      ColorHandler.init()

      MinecraftForge.EVENT_BUS.register(HighlightRenderer)
      MinecraftForge.EVENT_BUS.register(NanomachinesHandler.Client)
      MinecraftForge.EVENT_BUS.register(PetRenderer)
      MinecraftForge.EVENT_BUS.register(RackMountableRenderHandler)
      MinecraftForge.EVENT_BUS.register(Sound)
      MinecraftForge.EVENT_BUS.register(TextBuffer)
      MinecraftForge.EVENT_BUS.register(MFUTargetRenderer)
      MinecraftForge.EVENT_BUS.register(WirelessNetworkDebugRenderer)
      MinecraftForge.EVENT_BUS.register(Audio)
      MinecraftForge.EVENT_BUS.register(HologramRenderer)
      MinecraftForge.EVENT_BUS.register(ScreenRenderer)
    }): Runnable)

    RenderSystem.recordRenderCall(() => MinecraftForge.EVENT_BUS.register(TextBufferRenderCache))
  }

  @SubscribeEvent
  def onRegisterLayerDefinitions(event: EntityRenderersEvent.RegisterLayerDefinitions): Unit = {
    event.registerLayerDefinition(ModelQuadcopter.LAYER_LOCATION, () => ModelQuadcopter.createLayer())
  }

  @SubscribeEvent
  def onRegisterKeyMappings(event: RegisterKeyMappingsEvent): Unit = {
    event.register(KeyBindings.extendedTooltip)
    event.register(KeyBindings.analyzeCopyAddr)
    event.register(KeyBindings.clipboardPaste)
  }

  @SubscribeEvent
  def onRegisterRenderers(e: EntityRenderersEvent.RegisterRenderers): Unit = {
    e.registerEntityRenderer(EntityTypes.DRONE.get(), ctx => new DroneRenderer(ctx))

    BlockEntityRenderers.register(BlockEntityTypes.ADAPTER.get(), AdapterRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.ASSEMBLER.get(), AssemblerRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.CASE.get(), CaseRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.CHARGER.get(), ChargerRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.DISASSEMBLER.get(), DisassemblerRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.DISK_DRIVE.get(), DiskDriveRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.GEOLYZER.get(), GeolyzerRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.HOLOGRAM.get(), HologramRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.MICROCONTROLLER.get(), ctx => new MicrocontrollerRenderer(ctx))
    BlockEntityRenderers.register(BlockEntityTypes.NET_SPLITTER.get(), ctx => new NetSplitterRenderer(ctx))
    BlockEntityRenderers.register(BlockEntityTypes.POWER_DISTRIBUTOR.get(), PowerDistributorRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.PRINTER.get(), PrinterRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.RAID.get(), RaidRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.RACK.get(), RackRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.RELAY.get(), RelayRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.ROBOT.get(), RobotRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.SCREEN.get(), ScreenRenderer)
    BlockEntityRenderers.register(BlockEntityTypes.TRANSPOSER.get(), TransposerRenderer)
  }

  override def registerModel(instance: Item, id: String): Unit = ModelInitialization.registerModel(instance, id)

  override def registerModel(instance: Block, id: String): Unit = ModelInitialization.registerModel(instance, id)
}
