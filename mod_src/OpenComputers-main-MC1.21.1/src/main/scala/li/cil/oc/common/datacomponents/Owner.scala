package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}

import java.util.UUID

case class Owner(name: String, id: UUID)

object Owner {
  val CODEC: Codec[Owner] = RecordCodecBuilder.create(inst => inst.group(
    Codec.STRING.fieldOf("name").forGetter(_.name),
    UUIDUtil.CODEC.fieldOf("id").forGetter(_.id)
  ).apply(inst, Owner.apply _))

  val STREAM_CODEC: StreamCodec[ByteBuf, Owner] = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8, _.name,
    UUIDUtil.STREAM_CODEC, _.id,
    Owner.apply _
  )
}
