package li.cil.oc.client.renderer.font

import com.google.common.base.Charsets
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.font.TextureFontRenderer.Glyph
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

import scala.collection.mutable
import scala.io.Source

/**
 * 使用用户提供的贴图的字体渲染器：支持的字形固定（由 `chars.txt` 决定），但至少它是能用的。
 *
 * ==1.7.10 到 1.21.1 的变化==
 *  - `Minecraft#getMinecraft#getResourceManager#getResource(...).getInputStream` 改成
 *    `Optional<Resource>` + `Resource#open()`；贴图位置用
 *    `ResourceLocation.fromNamespaceAndPath` 构造。
 *  - `GL11.glTexCoord2d` / `glVertex3d` 的立即模式改由基类用
 *    `PoseStack` + `VertexConsumer` 提交，本类只回答「某个码点的字形在贴图的哪一块」。
 *  - 贴图不再用 `TextureManager#bindTexture` 预先绑定：1.21.1 的
 *    [net.minecraft.client.renderer.RenderType#text] 会按贴图位置绑定，
 *    所以这里只需要给出 `ResourceLocation`。
 */
class StaticFontRenderer extends TextureFontRenderer {
  private val (chars, cellWidth, cellHeightRaw) = StaticFontRenderer.readMetadata(basicChars)

  override protected def charWidth: Int = cellWidth

  override protected def charHeight: Int = cellHeightRaw

  /** 贴图按 256x256 处理，与 1.7.10 的 `cols = 256 / charWidth` 一致。 */
  override protected def textureSize: Int = 256

  /** 行高在字形高度上多留 1 像素，避免线性过滤时上下行渗色（沿用旧版的 `vStep`）。 */
  private val rowHeight = charHeight + 1

  private val cols = math.max(1, 256 / math.max(1, charWidth))

  private val scale = Settings.get.fontCharScale

  private val glyphs = mutable.Map.empty[Int, Glyph]

  override protected def glyphScale: Float = scale.toFloat

  override protected def textureLocation(page: Int): ResourceLocation =
    if (Settings.get.textAntiAlias) Textures.fontAntiAliased else Textures.fontAliased

  override protected def glyph(char: Int): Glyph = {
    if (glyphs.contains(char)) glyphs(char)
    else {
      val index = {
        val i = chars.indexOf(char)
        if (i < 0) chars.indexOf('?') else i
      }
      val glyph =
        if (index < 0 || charWidth <= 0 || charHeight <= 0) null
        else Glyph(0, (index % cols) * charWidth, (index / cols) * rowHeight, charWidth, charHeight)
      glyphs.put(char, glyph)
      glyph
    }
  }

  /**
   * 资源重载入口。
   *
   * 1.21.1 的贴图由 `RenderType#text` 按需绑定，不需要重新绑定；
   * 但 `chars.txt` 可能被资源包改掉，这里重读一次并在尺寸变化时给出提示。
   *
   * TODO(资源重载): 字符表与字形尺寸是构造期确定的 val，无法就地替换。
   * 如果资源包真的改了 `chars.txt`，需要重建 [StaticFontRenderer] 实例；
   * 目前只清空字形缓存并记录一条警告，不抛异常。
   */
  override def initialize(): Unit = {
    glyphs.clear()
    StaticFontRenderer.readMetadata(basicChars) match {
      case (newChars, newWidth, newHeight) =>
        if (newWidth != charWidth || newHeight != charHeight || newChars.length != chars.length) {
          OpenComputers.log.warn("Font metadata changed after reload, re-create the font renderer to apply it.")
        }
    }
  }
}

object StaticFontRenderer {
  /**
   * 读取 `assets/<domain>/textures/font/chars.txt`。
   *
   * 第一行是字符表，可选第二行是 `宽 高` 的字形尺寸；读不到时回退到默认值。
   *
   * @param fallbackChars 读不到文件时使用的字符表（调用方传基类的 `basicChars`）
   */
  private def readMetadata(fallbackChars: String): (String, Int, Int) = {
    try {
      val mc = Minecraft.getInstance
      if (mc == null) return (fallbackChars, DefaultWidth, DefaultHeight)
      val location = ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/font/chars.txt")
      val resource = mc.getResourceManager.getResource(location)
      if (!resource.isPresent) {
        OpenComputers.log.warn("Failed reading font metadata, using defaults.")
        return (fallbackChars, DefaultWidth, DefaultHeight)
      }
      val source = Source.fromInputStream(resource.get.open())(Charsets.UTF_8)
      try {
        val lines = source.getLines()
        val chars = if (lines.hasNext) lines.next() else fallbackChars
        val (w, h) = if (lines.hasNext) {
          val size = lines.next().split(" ", 2)
          if (size.length >= 2) (toInt(size(0), DefaultWidth), toInt(size(1), DefaultHeight))
          else (DefaultWidth, DefaultHeight)
        } else (DefaultWidth, DefaultHeight)
        (chars, w, h)
      } finally {
        source.close()
      }
    } catch {
      case t: Throwable =>
        OpenComputers.log.warn("Failed reading font metadata, using defaults.", t)
        (fallbackChars, DefaultWidth, DefaultHeight)
    }
  }

  private def toInt(value: String, fallback: Int): Int =
    try value.trim.toInt catch { case _: Throwable => fallback }

  /** 读不到 `chars.txt` 时使用的默认字形尺寸（旧版就是这两个值）。 */
  private val DefaultWidth = 10

  private val DefaultHeight = 18
}
