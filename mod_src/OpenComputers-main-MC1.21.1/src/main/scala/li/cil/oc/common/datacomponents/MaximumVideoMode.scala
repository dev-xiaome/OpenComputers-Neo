package li.cil.oc.common.datacomponents

import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec

case class MaximumVideoMode(width: Int, height: Int, depth: Int)

object MaximumVideoMode {
  val CODEC = RecordCodecBuilder.create[MaximumVideoMode](inst => inst.group(
    ScalaCodec.INT.fieldOf("width").forGetter(_.width),
    ScalaCodec.INT.fieldOf("height").forGetter(_.height),
    ScalaCodec.INT.fieldOf("depth").forGetter(_.depth)
  ).apply(inst, MaximumVideoMode.apply _))

  val STREAM_CODEC: StreamCodec[ByteBuf, MaximumVideoMode] = StreamCodec.composite(
    ScalaStreamCodec.VAR_INT, _.width,
    ScalaStreamCodec.VAR_INT, _.height,
    ScalaStreamCodec.VAR_INT, _.depth,
    MaximumVideoMode.apply _
  )
}
