package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec

case class DroneState(targetX: Float,
                      targetY: Float,
                      targetZ: Float,
                      targetAcceleration: Float,
                      selectedSlot: Byte,
                      selectedTank: Byte)

object DroneState {
  val CODEC: Codec[DroneState] = RecordCodecBuilder.create(inst => inst.group(
    ScalaCodec.FLOAT.fieldOf("targetX").forGetter(_.targetX),
    ScalaCodec.FLOAT.fieldOf("targetY").forGetter(_.targetY),
    ScalaCodec.FLOAT.fieldOf("targetZ").forGetter(_.targetZ),
    ScalaCodec.FLOAT.fieldOf("targetAcceleration").forGetter(_.targetAcceleration),
    ScalaCodec.BYTE.fieldOf("selectedSlot").forGetter(_.selectedSlot),
    ScalaCodec.BYTE.fieldOf("selectedTank").forGetter(_.selectedTank),
  ).apply(inst, DroneState.apply _))

  val STREAM_CODEC: StreamCodec[ByteBuf, DroneState] = StreamCodec.composite(
    ScalaStreamCodec.FLOAT, _.targetX,
    ScalaStreamCodec.FLOAT, _.targetY,
    ScalaStreamCodec.FLOAT, _.targetZ,
    ScalaStreamCodec.FLOAT, _.targetAcceleration,
    ScalaStreamCodec.BYTE, _.selectedSlot,
    ScalaStreamCodec.BYTE, _.selectedTank,
    DroneState.apply _
  )
}
