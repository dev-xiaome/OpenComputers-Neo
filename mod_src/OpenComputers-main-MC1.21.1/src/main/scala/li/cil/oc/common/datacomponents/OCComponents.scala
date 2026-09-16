package li.cil.oc.common.datacomponents

import li.cil.oc.api.ImmutableFluidStack

import com.mojang.serialization.Codec
import li.cil.oc.api.ImmutableItemStack
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.server.component.DebugCard.AccessContext
import net.minecraft.core.component.{DataComponentPatch, DataComponentType}
import net.minecraft.core.registries.Registries
import net.minecraft.core.{BlockPos, Direction, UUIDUtil}
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.{Component, ComponentSerialization}
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.ColorRGBA
import net.minecraft.world.item.{DyeColor, ItemStack}
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.registries.{DeferredHolder, DeferredRegister}

import java.nio.ByteBuffer
import java.util.UUID
import scala.language.implicitConversions

object OCComponents {
  //  private[oc] implicit def convert[T](holder: DeferredHolder[DataComponentType[_], DataComponentType[T]]): DataComponentType[T] =
  //    holder.get()

  private[oc] implicit def convert(itemStack: ItemStack): ImmutableItemStack =
    ImmutableItemStack.copyOf(itemStack)

  private[oc] implicit def convert(itemStack: ImmutableItemStack): ItemStack =
    itemStack.mutableCopy()

  type Type[T] = DeferredHolder[DataComponentType[_], DataComponentType[T]]

  val REGISTRAR: DeferredRegister.DataComponents = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "opencomputers")

  def init(modBus: IEventBus): Unit = {
    Network.initialize()
    REGISTRAR.register(modBus)
  }

  private def persistent[T](name: String, codec: Codec[T]): Type[T] =
    REGISTRAR.registerComponentType(name, _.persistent(codec))

  private def sharedOnly[T](name: String, streamCodec: StreamCodec[_ >: RegistryFriendlyByteBuf, T]): Type[T] =
    REGISTRAR.registerComponentType(name, _.networkSynchronized(streamCodec))

  private def persistentShared[T](name: String, codec: Codec[T], streamCodec: StreamCodec[_ >: RegistryFriendlyByteBuf, T]): Type[T] =
    REGISTRAR.registerComponentType(name, _.persistent(codec).networkSynchronized(streamCodec))

  private def persistentUnit(name: String): Type[Unit] =
    persistent(name, Codec.unit(()))

  private def persistentSharedUnit(name: String): Type[Unit] =
    persistentShared(name, Codec.unit(()), StreamCodec.unit(()))

  val ID: Type[UUID] = persistentShared("id", UUIDUtil.CODEC, UUIDUtil.STREAM_CODEC)
  val OWNER: Type[Owner] = persistentShared("owner", Owner.CODEC, Owner.STREAM_CODEC)
  val DISK_COLOR: Type[DyeColor] = persistentShared("disk_color", DyeColor.CODEC, DyeColor.STREAM_CODEC)
  val LOOT_DISK: Type[ResourceLocation] = persistentShared("loot_disk", ResourceLocation.CODEC, ResourceLocation.STREAM_CODEC)
  val LABEL: Type[String] = persistentShared("label", Codec.STRING, ByteBufCodecs.STRING_UTF8)
  val READONLY: Type[Boolean] = persistentShared("readonly", ScalaCodec.BOOL, ScalaStreamCodec.BOOL)
  val EEPROM_CODE: Type[ByteBuffer] = persistent("eeprom_code", Codec.BYTE_BUFFER)
  val EEPROM_DATA: Type[ByteBuffer] = persistent("eeprom_data", Codec.BYTE_BUFFER)
  val MF_COORD: Type[MFCoords] = persistentShared("mf_coord", MFCoords.CODEC, MFCoords.STREAM_CODEC)
  val COMPONENTS: Type[List[ImmutableItemStack]] = persistentShared("components", ScalaCodec.list(ImmutableItemStack.OPTIONAL_CODEC), ScalaStreamCodec.list(ImmutableItemStack.OPTIONAL_STREAM_CODEC))
  val CONTENTS: Type[List[ImmutableItemStack]] = persistentShared("contents", ScalaCodec.list(ImmutableItemStack.OPTIONAL_CODEC), ScalaStreamCodec.list(ImmutableItemStack.OPTIONAL_STREAM_CODEC))
  val CONTAINERS: Type[List[ImmutableItemStack]] = persistentShared("containers", ScalaCodec.list(ImmutableItemStack.OPTIONAL_CODEC), ScalaStreamCodec.list(ImmutableItemStack.OPTIONAL_STREAM_CODEC))
  val ATTACHMENT: Type[ImmutableItemStack] = persistentShared("attachment", ImmutableItemStack.OPTIONAL_CODEC, ImmutableItemStack.OPTIONAL_STREAM_CODEC)
  val TIER: Type[Byte] = persistentShared("tier", ScalaCodec.BYTE, ScalaStreamCodec.BYTE)
  val STORED_ENERGY: Type[Int] = persistentShared("stored_energy", ScalaCodec.INT, ScalaStreamCodec.VAR_INT)
  val ADDRESS: Type[String] = persistentShared("address", Codec.STRING, ByteBufCodecs.STRING_UTF8)
  val VISIBILITY: Type[Visibility] = persistentShared("visibility", Visibility.CODEC, Visibility.STREAM_CODEC)
  val UNMANAGED: Type[Boolean] = persistentShared("unmanaged", ScalaCodec.BOOL, ScalaStreamCodec.BOOL)
  val LOCK: Type[String] = persistentShared("lock", Codec.STRING, ByteBufCodecs.STRING_UTF8)
  val CHARGE: Type[Double] = persistentShared("charge", ScalaCodec.DOUBLE, ScalaStreamCodec.DOUBLE)
  val MAX_CHARGE: Type[Double] = persistentShared("max_charge", ScalaCodec.DOUBLE, ScalaStreamCodec.DOUBLE)
  val ROBOT_CHARGE: Type[RobotChargeInfo] = persistentShared("robot_charge", RobotChargeInfo.CODEC, RobotChargeInfo.STREAM_CODEC)
  val NANOMACHINES_NETWORK_INFO: Type[CompoundTag] = persistent("nanomachines_network_info", CompoundTag.CODEC)
  val SOURCE_MAP_ITEM: Type[ImmutableItemStack] = persistent("source_map_item", ImmutableItemStack.OPTIONAL_CODEC)
  val KEYS: Type[List[String]] = persistentShared("keys", ScalaCodec.list(Codec.STRING), ScalaStreamCodec.list(ByteBufCodecs.STRING_UTF8))
  val TERMINAL_REFERENCE: Type[TerminalReference] = persistent("terminal_reference", TerminalReference.CODEC)
  val TERMINAL_SERVER_BUFFER: Type[DataComponentPatch] = persistentShared("terminal_server_buffer", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val TERMINAL_SERVER_KEYBOARD: Type[DataComponentPatch] = persistentShared("terminal_server_keyboard", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val RACK_KVM_BUFFER_0: Type[DataComponentPatch] = persistentShared("rack_kvm_buffer_0", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val RACK_KVM_BUFFER_1: Type[DataComponentPatch] = persistentShared("rack_kvm_buffer_1", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val RACK_KVM_BUFFER_2: Type[DataComponentPatch] = persistentShared("rack_kvm_buffer_2", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val RACK_KVM_KEYBOARD_0: Type[DataComponentPatch] = persistentShared("rack_kvm_keyboard_0", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val RACK_KVM_KEYBOARD_1: Type[DataComponentPatch] = persistentShared("rack_kvm_keyboard_1", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val RACK_KVM_KEYBOARD_2: Type[DataComponentPatch] = persistentShared("rack_kvm_keyboard_2", DataComponentPatch.CODEC, DataComponentPatch.STREAM_CODEC)
  val RACK_KVM_SELECTED_SLOT: Type[Int] = persistentShared("rack_kvm_selected_slot", ScalaCodec.INT, ScalaStreamCodec.INT)
  val RACK_KVM_SERVER_MASK: Type[Int] = persistentShared("rack_kvm_server_mask", ScalaCodec.INT, ScalaStreamCodec.INT)
  val TEXT_BUFFER: Type[TextBufferContents] = persistent("text_buffer", TextBufferContents.CODEC)
  val IS_ON: Type[Boolean] = persistentShared("is_on", ScalaCodec.BOOL, ScalaStreamCodec.BOOL)
  val IS_RUNNING: Type[Boolean] = persistentShared("is_running", ScalaCodec.BOOL, ScalaStreamCodec.BOOL)
  val IS_POWERED: Type[Boolean] = persistentShared("is_powered", ScalaCodec.BOOL, ScalaStreamCodec.BOOL)
  val IS_ERRORED: Type[Unit] = persistentSharedUnit("is_errored")
  val USERS: Type[Set[String]] = persistentShared("users", ScalaCodec.set(Codec.STRING), ScalaStreamCodec.set(ByteBufCodecs.STRING_UTF8))
  val MAX_VIDEO_MODE: Type[MaximumVideoMode] = persistentShared("max_video_mode", MaximumVideoMode.CODEC, MaximumVideoMode.STREAM_CODEC)
  val VIDEO_MODE: Type[VideoMode] = persistentShared("video_mode", VideoMode.CODEC, VideoMode.STREAM_CODEC)
  val IS_PRECISE: Type[Boolean] = persistentShared("is_precise", ScalaCodec.BOOL, ScalaStreamCodec.BOOL)
  val MACHINE: Type[MachineData] = persistent("machine", MachineData.CODEC)
  val DRONE_STATE: Type[DroneState] = persistentShared("drone_state", DroneState.CODEC, DroneState.STREAM_CODEC)
  val STATUS_TEXT: Type[Component] = persistentShared("status_text", ComponentSerialization.FLAT_CODEC, ComponentSerialization.STREAM_CODEC)
  val LIGHT_COLOR: Type[ColorRGBA] = persistentShared("light_color", ColorRGBA.CODEC, ScalaStreamCodec.COLOR_RGBA)
  val ROBOT_FLAG: Type[ResourceLocation] = persistentShared("robot_flag", ResourceLocation.CODEC, ResourceLocation.STREAM_CODEC)
  val PRINT: Type[PrintData] = persistentShared("print", PrintData.CODEC, PrintData.STREAM_CODEC)
  val FILESYSTEM_DATA: Type[CompoundTag] = persistent("filesystem", CompoundTag.CODEC)
  val ROBOT_ROM_FILESYSTEM_DATA: Type[CompoundTag] = persistent("robot_rom_filesystem", CompoundTag.CODEC)
  val HANDLES: Type[Map[String, Set[Int]]] = persistent("handles", ScalaCodec.map(Codec.STRING, ScalaCodec.set(ScalaCodec.INT)))
  val COMPOUND_DRIVER: Type[(Long, Map[String, CompoundStorage])] = persistent("compound_driver", ScalaCodec.pair(ScalaCodec.LONG -> ScalaCodec.map(Codec.STRING, CompoundStorage.CODEC)))
  val PALETTE: Type[List[Int]] = persistentShared("palette", ScalaCodec.list(ScalaCodec.INT), ScalaStreamCodec.list(ScalaStreamCodec.INT))
  val GRAPHICS_CARD: Type[GraphicsCardState] = persistent("graphics_card", GraphicsCardState.CODEC)
  val VIDEO_RAM: Type[List[(Int, CompoundStorage)]] = persistent("video_ram", ScalaCodec.list(ScalaCodec.pair(ScalaCodec.INT -> CompoundStorage.CODEC)))
  val WAKE_THRESHOLD: Type[Int] = persistent("wake_threshold", ScalaCodec.INT)
  val HAS_REDSTONE_INPUT: Type[Boolean] = persistent("has_redstone_input", ScalaCodec.BOOL)
  val INVERT_TOUCH: Type[Unit] = persistentSharedUnit("invert_touch")
  val WIRELESS_REDSTONE_STATE: Type[WirelessRedstoneState] = persistent("wireless_redstone_state", WirelessRedstoneState.CODEC)
  val LEASHED_ENTITIES: Type[List[UUID]] = persistent("leashed_entities", ScalaCodec.list(UUIDUtil.CODEC))
  val OPEN_PORTS: Type[List[Int]] = persistent("open_ports", ScalaCodec.list(ScalaCodec.INT))
  val WAKE_MESSAGE: Type[WakeMessage] = persistent("wake_message", WakeMessage.CODEC)
  val TUNNEL: Type[String] = persistent("tunnel", Codec.STRING)
  val STRENGTH: Type[Double] = persistent("strength", ScalaCodec.DOUBLE)
  val HEAD_POS: Type[Int] = persistent("head_position", ScalaCodec.INT)
  val TANK: Type[ImmutableFluidStack] = persistent("tank", ImmutableFluidStack.CODEC)
  val FUEL_INVENTORY: Type[ImmutableItemStack] = persistent("fuel_inventory", ImmutableItemStack.OPTIONAL_CODEC)
  val FUEL_TICKS_REMAINING: Type[Int] = persistent("fuel_ticks_remaining", ScalaCodec.INT)
  val RENDER_COLOR: Type[ColorRGBA] = persistentShared("render_color", ColorRGBA.CODEC, ScalaStreamCodec.COLOR_RGBA)
  val CHARGE_SPEED: Type[Double] = persistentShared("charge_speed", ScalaCodec.DOUBLE, ScalaStreamCodec.DOUBLE)
  val INVERT_SIGNAL: Type[Unit] = persistentUnit("invert_signal")
  val PRESENCE: Type[ByteBuffer] = persistent("presence", Codec.BYTE_BUFFER)
  val COMPONENT_NODES: Type[List[Option[CompoundStorage]]] = persistent("component_nodes", ScalaCodec.list(CompoundStorage.OPTION_CODEC))
  val SELECTED_SLOT: Type[Int] = persistentShared("selected_slot", ScalaCodec.INT, ScalaStreamCodec.INT)
  val SELECTED_TANK: Type[Int] = persistent("selected_tank", ScalaCodec.INT)
  val ROBOT_TOTAL_ANIMATION_TIME: Type[Int] = persistentShared("robot/total_animation_time", ScalaCodec.INT, ScalaStreamCodec.INT)
  val ROBOT_CURRENT_ANIMATION: Type[RobotCurrentAnimation] = persistentShared("robot/current_animation", RobotCurrentAnimation.CODEC, RobotCurrentAnimation.STREAM_CODEC)
  val RACK_DATA: Type[List[Option[CompoundStorage]]] = persistentShared("rack_data", ScalaCodec.list(CompoundStorage.OPTION_CODEC), ScalaStreamCodec.list(CompoundStorage.OPTION_STREAM_CODEC))
  val RELAY_ENABLED: Type[Unit] = persistentUnit("relay_enabled")
  val RACK_NODE_MAPPING: Type[List[List[Direction]]] = persistent("rack_node_mapping", ScalaCodec.list(ScalaCodec.list(Direction.CODEC)))
  val DEBUG_CARD_ACCESS_CONTEXT: Type[AccessContext] = persistent("debug_card/access_context", AccessContext.CODEC)
  val DEBUG_CARD_REMOTE_NODE_POSITION: Type[BlockPos] = persistent("debug_card/remote_node", BlockPos.CODEC)
  val CHAMELIUM_COLOR: Type[DyeColor] = persistentShared("chamelium_color", DyeColor.CODEC, DyeColor.STREAM_CODEC)

  object Network {
    val LAST_ACCESS: Type[Long] = sharedOnly("network/last_access_timestamp", ScalaStreamCodec.VAR_LONG)
    val LAST_DISK_ACCESS: Type[Long] = sharedOnly("network/last_disk_access", ScalaStreamCodec.VAR_LONG)
    val LAST_NETWORK_ACCESS: Type[Long] = sharedOnly("network/last_network_access", ScalaStreamCodec.VAR_LONG)
    val DISK_ITEM: Type[ImmutableItemStack] = sharedOnly("network/disk_item", ImmutableItemStack.OPTIONAL_STREAM_CODEC)

    private[datacomponents] def initialize(): Unit = ()
  }
}
