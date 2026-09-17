package li.cil.oc.common

import li.cil.oc.OpenComputers
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * NeoForge 1.21.1 的统一包体。
 *
 * Forge 1.20 时代的 SimpleChannel 只在通道上注册了一个 byte[] 消息类型，包体自身
 * 用首字节标记 PacketType。这里保持完全相同的「一个类型 + 首字节 PacketType」布局，
 * 只是把载体从 SimpleChannel 换成 CustomPacketPayload，于是 PacketBuilder、
 * PacketType 以及几百个收发调用点都不需要改动。
 *
 * data 的布局与旧版逐字节一致：第 0 字节是压缩标志，第 1 字节是 PacketType.id。
 */
class PacketPayload(val data: Array[Byte]) extends CustomPacketPayload {
  override def `type`(): CustomPacketPayload.Type[_ <: CustomPacketPayload] = PacketPayload.TYPE
}

object PacketPayload {
  val TYPE: CustomPacketPayload.Type[PacketPayload] = new CustomPacketPayload.Type[PacketPayload](
    ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "packet"))

  /**
   * 编解码器：长度前缀 + 原始字节。
   *
   * 固定用 RegistryFriendlyByteBuf 作为缓冲类型，正好满足 PayloadRegistrar 需要的
   * StreamCodec 上界，省掉一次存在类型推断。
   */
  val STREAM_CODEC: StreamCodec[RegistryFriendlyByteBuf, PacketPayload] =
    StreamCodec.of[RegistryFriendlyByteBuf, PacketPayload](
      (buf: RegistryFriendlyByteBuf, payload: PacketPayload) => {
        buf.writeInt(payload.data.length)
        buf.writeBytes(payload.data)
      },
      (buf: RegistryFriendlyByteBuf) => {
        val length = buf.readInt()
        val bytes = new Array[Byte](length)
        buf.readBytes(bytes)
        new PacketPayload(bytes)
      }
    )
}
