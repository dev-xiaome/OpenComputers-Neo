package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

case class WirelessRedstoneState(frequency: Int, input: Boolean, output: Boolean)

object WirelessRedstoneState {
  val CODEC: Codec[WirelessRedstoneState] = RecordCodecBuilder.create(inst => inst.group(
    ScalaCodec.INT.fieldOf("frequency").forGetter(_.frequency),
    ScalaCodec.BOOL.fieldOf("input").forGetter(_.input),
    ScalaCodec.BOOL.fieldOf("output").forGetter(_.output),
  ).apply(inst, WirelessRedstoneState.apply _))
}
