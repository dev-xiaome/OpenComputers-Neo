package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import li.cil.oc.common.datacomponents.TextBufferContents.ShortArray

import java.nio.{ByteBuffer, ShortBuffer}

case class TextBufferContents(width: Int,
                              height: Int,
                              depth: Int,
                              foreground: Int,
                              foregroundIsPalette: Boolean,
                              background: Int,
                              backgroundIsPalette: Boolean,
                              buffer: List[String],
                              colors: ShortArray)

object TextBufferContents {
  final class ShortArray(values: Array[Short]) {
    private val array = values.clone()

    def this(array: Array[Array[Short]]) = this(array.flatten)

    def this(buf: ShortBuffer) = {
      this(Array.fill[Short](buf.remaining()) { 0 })
      buf.get(array)
    }

    def this(buf: ByteBuffer) = {
      this(buf.asShortBuffer())
    }

    def intoByteBuffer(): ByteBuffer = {
      val bytes = ByteBuffer.allocate(array.length * 2)
      bytes.asShortBuffer().put(array)
      bytes
    }

    def sized(width: Int, height: Int): Array[Array[Short]] =
      Array.tabulate[Short](height, width) { (y, x) =>
        val index = y * width + x
        if (index < array.length) array(index) else 0
      }

    override def equals(other: Any): Boolean = other match {
      case that: ShortArray => java.util.Arrays.equals(array, that.array)
      case _ => false
    }

    override def hashCode(): Int = java.util.Arrays.hashCode(array)
  }

  object ShortArray {
    val CODEC: Codec[ShortArray] = Codec.BYTE_BUFFER.xmap(buf => new ShortArray(buf), array => array.intoByteBuffer())
  }

  val CODEC: Codec[TextBufferContents] = RecordCodecBuilder.create(inst => inst.group(
    ScalaCodec.INT.fieldOf("width").forGetter(_.width),
    ScalaCodec.INT.fieldOf("height").forGetter(_.height),
    ScalaCodec.INT.fieldOf("depth").forGetter(_.depth),
    ScalaCodec.INT.fieldOf("foreground").forGetter(_.foreground),
    ScalaCodec.BOOL.fieldOf("foregroundIsPalette").forGetter(_.foregroundIsPalette),
    ScalaCodec.INT.fieldOf("background").forGetter(_.background),
    ScalaCodec.BOOL.fieldOf("backgroundIsPalette").forGetter(_.backgroundIsPalette),
    ScalaCodec.list(Codec.STRING).fieldOf("buffer").forGetter(_.buffer),
    ShortArray.CODEC.fieldOf("colors").forGetter(_.colors)
  ).apply(inst, TextBufferContents.apply _))
}
