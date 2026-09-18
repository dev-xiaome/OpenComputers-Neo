package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

case class WakeMessage(message: String, fuzzy: Boolean)

object WakeMessage {
  val CODEC: Codec[WakeMessage] = RecordCodecBuilder.create(inst => inst.group(
    Codec.STRING.fieldOf("message").forGetter(_.message),
    ScalaCodec.BOOL.fieldOf("fuzzy").forGetter(_.fuzzy)
  ).apply(inst, WakeMessage.apply _))
}
