package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.StreamCodec

case class RobotCurrentAnimation(ticksLeft: Int,
                                 moveFrom: Option[BlockPos],
                                 swingShovel: Boolean,
                                 turnAxis: Byte)

object RobotCurrentAnimation {
  val CODEC: Codec[RobotCurrentAnimation] = RecordCodecBuilder.create(inst => inst.group(
    ScalaCodec.INT.fieldOf("ticks_left").forGetter(_.ticksLeft),
    ScalaCodec.optionFieldOf("move_from", BlockPos.CODEC).forGetter(_.moveFrom),
    ScalaCodec.BOOL.fieldOf("swing_shovel").forGetter(_.swingShovel),
    ScalaCodec.BYTE.fieldOf("turn_axis").forGetter(_.turnAxis)
  ).apply(inst, RobotCurrentAnimation.apply _))

  val STREAM_CODEC: StreamCodec[ByteBuf, RobotCurrentAnimation] = StreamCodec.composite(
    ScalaStreamCodec.INT, _.ticksLeft,
    ScalaStreamCodec.option(BlockPos.STREAM_CODEC), _.moveFrom,
    ScalaStreamCodec.BOOL, _.swingShovel,
    ScalaStreamCodec.BYTE, _.turnAxis,
    RobotCurrentAnimation.apply _
  )
}
