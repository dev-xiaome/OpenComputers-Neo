package li.cil.oc.common.datacomponents

import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec

case class VideoMode(width: Int, height: Int)

object VideoMode {
  val CODEC = RecordCodecBuilder.create[VideoMode](inst => inst.group(
    ScalaCodec.INT.fieldOf("width").forGetter(_.width),
    ScalaCodec.INT.fieldOf("height").forGetter(_.height)
  ).apply(inst, VideoMode.apply _))

  val STREAM_CODEC: StreamCodec[ByteBuf, VideoMode] = StreamCodec.composite(
    ScalaStreamCodec.VAR_INT, _.width,
    ScalaStreamCodec.VAR_INT, _.height,
    VideoMode.apply _
  )
}
