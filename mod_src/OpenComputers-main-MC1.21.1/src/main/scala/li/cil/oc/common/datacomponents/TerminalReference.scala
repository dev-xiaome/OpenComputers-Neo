package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

case class TerminalReference(key: String, server: String)

object TerminalReference {
  val CODEC = RecordCodecBuilder.create[TerminalReference](inst => inst.group(
    Codec.STRING.fieldOf("key").forGetter(_.key),
    Codec.STRING.fieldOf("server").forGetter(_.server)
  ).apply(inst, TerminalReference.apply _))
}
