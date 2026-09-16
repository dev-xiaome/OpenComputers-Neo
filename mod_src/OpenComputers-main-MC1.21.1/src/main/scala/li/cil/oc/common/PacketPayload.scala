package li.cil.oc.common

import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import io.netty.buffer.ByteBuf

class PacketPayload(val data: Array[Byte]) extends CustomPacketPayload {
  override def `type`(): CustomPacketPayload.Type[_ <: CustomPacketPayload] = PacketPayload.TYPE
}

object PacketPayload {
  val TYPE = new CustomPacketPayload.Type[PacketPayload](
    ResourceLocation.fromNamespaceAndPath("opencomputers", "packet"))

  val STREAM_CODEC: StreamCodec[ByteBuf, PacketPayload] =
    StreamCodec.of(
      (buf, payload) => {
        buf.writeInt(payload.data.length)
        buf.writeBytes(payload.data)
      },
      buf => {
        val len = buf.readInt()
        val bytes = new Array[Byte](len)
        buf.readBytes(bytes)
        new PacketPayload(bytes)
      }
    )
}