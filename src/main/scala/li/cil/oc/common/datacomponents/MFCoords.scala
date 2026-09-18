package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

case class MFCoords(dimension: ResourceLocation, blockPos: BlockPos, side: Direction)

object MFCoords {
  val CODEC: Codec[MFCoords] = RecordCodecBuilder.create[MFCoords](inst => inst.group(
    ResourceLocation.CODEC.fieldOf("dimension").forGetter(_.dimension),
    BlockPos.CODEC.fieldOf("position").forGetter(_.blockPos),
    Direction.CODEC.fieldOf("side").forGetter(_.side),
  ).apply(inst, MFCoords.apply _))

  val STREAM_CODEC: StreamCodec[ByteBuf, MFCoords] = StreamCodec.composite(
    ResourceLocation.STREAM_CODEC, _.dimension,
    BlockPos.STREAM_CODEC, _.blockPos,
    Direction.STREAM_CODEC, _.side,
    MFCoords.apply _
  )
}
