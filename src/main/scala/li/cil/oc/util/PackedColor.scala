package li.cil.oc.util

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Persistable
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

object PackedColor {

  object Depth {
    def bits(depth: api.internal.TextBuffer.ColorDepth) = depth match {
      case api.internal.TextBuffer.ColorDepth.OneBit => 1
      case api.internal.TextBuffer.ColorDepth.FourBit => 4
      case api.internal.TextBuffer.ColorDepth.EightBit => 8
      case api.internal.TextBuffer.ColorDepth.SixteenBit => 16
    }

    def format(depth: api.internal.TextBuffer.ColorDepth) = depth match {
      case api.internal.TextBuffer.ColorDepth.OneBit => SingleBitFormat
      case api.internal.TextBuffer.ColorDepth.FourBit => new MutablePaletteFormat
      case api.internal.TextBuffer.ColorDepth.EightBit => new HybridFormat
      case api.internal.TextBuffer.ColorDepth.SixteenBit => new SixteenBitFormat
    }
  }

  private val rShift32 = 16
  private val gShift32 = 8
  private val bShift32 = 0

  private def extract(value: Int) = {
    val r = (value >>> rShift32) & 0xFF
    val g = (value >>> gShift32) & 0xFF
    val b = (value >>> bShift32) & 0xFF
    (r, g, b)
  }

  trait ColorFormat extends Persistable {
    def depth: api.internal.TextBuffer.ColorDepth

    def inflate(value: Int): Int

    def validate(value: Color): Unit = {
      if (value.isPalette) {
        throw new IllegalArgumentException("color palette not supported")
      }
    }

    def deflate(value: Color): Byte

    def isFromPalette(value: Int): Boolean = false

    override def loadData(holder: DataComponentHolder): Unit = {}

    override def saveData(holder: MutableDataComponentHolder): Unit = {}
  }

  class SingleBitFormat(val color: Int) extends ColorFormat {
    override def depth = api.internal.TextBuffer.ColorDepth.OneBit

    override def inflate(value: Int) = if (value == 0) 0x000000 else color

    override def deflate(value: Color) = {
      (if (value.value == 0) 0 else 1).toByte
    }
  }

  object SingleBitFormat extends SingleBitFormat(Settings.get.monochromeColor)

  abstract class PaletteFormat extends ColorFormat {
    override def inflate(value: Int) = palette(math.max(0, math.min(palette.length - 1, value)))

    override def validate(value: Color): Unit = {
      if (value.isPalette && (value.value < 0 || value.value >= palette.length)) {
        throw new IllegalArgumentException("invalid palette index")
      }
    }

    override def deflate(value: Color) =
      if (value.isPalette) (math.max(0, value.value) % palette.length).toByte
      else palette.map(delta(value.value, _)).zipWithIndex.minBy(_._1)._2.toByte

    override def isFromPalette(value: Int) = true

    protected def palette: Array[Int]

    protected def delta(colorA: Int, colorB: Int) = {
      val (rA, gA, bA) = extract(colorA)
      val (rB, gB, bB) = extract(colorB)
      val dr = rA - rB
      val dg = gA - gB
      val db = bA - bB
      0.2126 * dr * dr + 0.7152 * dg * dg + 0.0722 * db * db
    }
  }

  class MutablePaletteFormat extends PaletteFormat {
    override def depth = api.internal.TextBuffer.ColorDepth.FourBit

    def apply(index: Int) = palette(index)

    def update(index: Int, value: Int) = palette(index) = value

    protected val palette = Array(
      0xFFFFFF, 0xFFCC33, 0xCC66CC, 0x6699FF,
      0xFFFF33, 0x33CC33, 0xFF6699, 0x333333,
      0xCCCCCC, 0x336699, 0x9933CC, 0x333399,
      0x663300, 0x336600, 0xFF3333, 0x000000)

    override def loadData(holder: DataComponentHolder): Unit = {
      super.loadData(holder)

      for(colors <- holder.getComponent(OCComponents.PALETTE)) {
        for ((color, index) <- colors.take(palette.length).zipWithIndex) palette(index) = color
      }
    }

    override def saveData(holder: MutableDataComponentHolder): Unit = {
      super.saveData(holder)

      holder.setComponent(OCComponents.PALETTE, palette.toList)
    }
  }

  class HybridFormat extends MutablePaletteFormat {
    private val reds = 6
    private val greens = 8
    private val blues = 5

    private val staticPalette = new Array[Int](240)

    {
      for (index <- staticPalette.indices) {
        val idxB = index % blues
        val idxG = (index / blues) % greens
        val idxR = (index / blues / greens) % reds
        val r = (idxR * 0xFF / (reds - 1.0) + 0.5).toInt
        val g = (idxG * 0xFF / (greens - 1.0) + 0.5).toInt
        val b = (idxB * 0xFF / (blues - 1.0) + 0.5).toInt
        staticPalette(index) = (r << rShift32) | (g << gShift32) | (b << bShift32)
      }
    }

    // Initialize palette to grayscale, excluding black and white, because
    // those are already contained in the normal color cube.
    for (i <- palette.indices) {
      val shade = 0xFF * (i + 1) / (palette.length + 1)
      this (i) = (shade << rShift32) | (shade << gShift32) | (shade << bShift32)
    }

    override def depth = api.internal.TextBuffer.ColorDepth.EightBit

    override def inflate(value: Int) =
      if (isFromPalette(value)) super.inflate(value)
      else staticPalette((value - palette.length) % 240)

    override def deflate(value: Color) = {
      val paletteIndex = super.deflate(value)
      if (value.isPalette) paletteIndex
      else {
        val (r, g, b) = extract(value.value)
        val idxR = (r * (reds - 1.0) / 0xFF + 0.5).toInt
        val idxG = (g * (greens - 1.0) / 0xFF + 0.5).toInt
        val idxB = (b * (blues - 1.0) / 0xFF + 0.5).toInt
        val deflated = (palette.length + idxR * greens * blues + idxG * blues + idxB).toByte
        if (delta(inflate(deflated & 0xFF), value.value) < delta(inflate(paletteIndex & 0xFF), value.value)) {
          deflated
        }
        else {
          paletteIndex
        }
      }
    }

    override def isFromPalette(value: Int) = value >= 0 && value < palette.length
  }

  class SixteenBitFormat extends MutablePaletteFormat {
    override def depth = api.internal.TextBuffer.ColorDepth.SixteenBit

    // RGB565: R=5bit, G=6bit, B=5bit = 65536色
    private val rBits = 5
    private val gBits = 6
    private val bBits = 5

    private def toRGB565(value: Int): Int = {
      val (r, g, b) = extract(value)
      val r5 = (r * ((1 << rBits) - 1.0) / 0xFF + 0.5).toInt
      val g6 = (g * ((1 << gBits) - 1.0) / 0xFF + 0.5).toInt
      val b5 = (b * ((1 << bBits) - 1.0) / 0xFF + 0.5).toInt
      (r5 << (gBits + bBits)) | (g6 << bBits) | b5
    }

    private def fromRGB565(value: Int): Int = {
      val r5 = (value >>> (gBits + bBits)) & 0x1F
      val g6 = (value >>> bBits) & 0x3F
      val b5 = value & 0x1F
      val r = (r5 * 0xFF / ((1 << rBits) - 1.0) + 0.5).toInt
      val g = (g6 * 0xFF / ((1 << gBits) - 1.0) + 0.5).toInt
      val b = (b5 * 0xFF / ((1 << bBits) - 1.0) + 0.5).toInt
      (r << rShift32) | (g << gShift32) | (b << bShift32)
    }

    override def inflate(value: Int): Int =
      if (isFromPalette(value)) super.inflate(value)
      else fromRGB565(value - palette.length)

    override def deflate(value: Color): Byte = {
      val paletteIndex = super.deflate(value)
      if (value.isPalette) paletteIndex
      else {
        val deflated = (palette.length + toRGB565(value.value)).toByte
        if (delta(inflate(deflated & 0xFF), value.value) < delta(inflate(paletteIndex & 0xFF), value.value))
          deflated
        else
          paletteIndex
      }
    }

    override def isFromPalette(value: Int) = value >= 0 && value < palette.length
  }

  case class Color(value: Int, isPalette: Boolean = false)

  // Colors are packed: 0xFFBB (F = foreground, B = background)
  val ForegroundShift = 8
  val BackgroundMask = 0x000000FF

  def pack(foreground: Color, background: Color, format: ColorFormat) = {
    (((format.deflate(foreground) & 0xFF) << ForegroundShift) | (format.deflate(background) & 0xFF)).toShort
  }

  def extractForeground(color: Short) = (color & 0xFFFF) >>> ForegroundShift

  def extractBackground(color: Short) = color & BackgroundMask

  def unpackForeground(color: Short, format: ColorFormat) =
    format.inflate(extractForeground(color))

  def unpackBackground(color: Short, format: ColorFormat) =
    format.inflate(extractBackground(color))
}
