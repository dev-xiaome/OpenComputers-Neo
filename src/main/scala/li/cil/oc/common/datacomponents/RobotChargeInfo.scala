package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec

case class RobotChargeInfo(max: Int, have: Int)

object RobotChargeInfo {
  val CODEC: Codec[RobotChargeInfo] = RecordCodecBuilder.create(inst => inst.group(
    ScalaCodec.INT.fieldOf("max").forGetter(_.max),
    ScalaCodec.INT.fieldOf("have").forGetter(_.have)
  ).apply(inst, RobotChargeInfo.apply _))

  val STREAM_CODEC: StreamCodec[ByteBuf, RobotChargeInfo] = StreamCodec.composite(
    ScalaStreamCodec.VAR_INT, _.max,
    ScalaStreamCodec.VAR_INT, _.have,
    RobotChargeInfo.apply _
  )
}
