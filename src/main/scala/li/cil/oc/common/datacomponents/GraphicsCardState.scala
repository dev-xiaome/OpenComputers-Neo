package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

case class GraphicsCardState(screen: Option[String], bufferIndex: Int)

object GraphicsCardState {
  val CODEC: Codec[GraphicsCardState] = RecordCodecBuilder.create(inst => inst.group(
    ScalaCodec.optionFieldOf("screen", Codec.STRING).forGetter(_.screen),
    ScalaCodec.INT.fieldOf("bufferIndex").forGetter(_.bufferIndex)
  ).apply(inst, GraphicsCardState.apply _))
}
