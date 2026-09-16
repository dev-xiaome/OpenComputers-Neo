package li.cil.oc.client.renderer.font

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.client.renderer.font.TextureFontRenderer.Glyph
import li.cil.oc.util.{ExtendedUnicodeHelper, PackedColor, TextBuffer}
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.resources.ResourceLocation

/**
 * 纹理字体渲染器的公共基类：静态字体（用现成的贴图）与动态字体（运行时生成图集）共用这里的分派逻辑。
 *
 * ==1.7.10 到 1.21.1 的结构变化==
 *  - 旧版用 `GL11.glBegin(GL11.GL_QUADS)` + `glTexCoord2d` / `glVertex3d` 立即模式逐字形提交，
 *    背景与前景都在同一个立即模式批次里。1.21.1 没有固定管线，改成
 *    [[com.mojang.blaze3d.vertex.PoseStack]] + [[net.minecraft.client.renderer.MultiBufferSource]]：
 *    背景四边形走 [net.minecraft.client.renderer.RenderType#textBackground]，
 *    字形走 [net.minecraft.client.renderer.RenderType#text]（顶点格式是 `POSITION_COLOR_TEX_LIGHTMAP`，
 *    因此只能写 `setColor` / `setUv` / `setLight`，不能写 overlay 或 normal）。
 *  - 旧版用 `GL11.glColor3ub` 切换前景色，现在改成写进顶点颜色；字形贴图本身是「白色不透明 / 全透明」，
 *    着色器会把它与顶点颜色相乘，于是每个字形可以有自己的颜色。
 *  - 旧版用 `GL11.glScalef(0.5, 0.5, 1)` 把 8x16 的字形缩到屏幕上 4x8；
 *    这里保留同样的语义，用 `PoseStack#scale`，这样 [charRenderWidth] / [charRenderHeight]
 *    的含义（GUI 里据此把鼠标位置换算成行列）与旧版完全一致。
 *  - 旧版把纹理坐标直接算出来传给 GL；这里改成 [Glyph] 描述字形在纹理页里的像素矩形，
 *    UV 在提交顶点时按 [textureSize] 归一化。
 */
abstract class TextureFontRenderer {
  protected final val basicChars = """☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼ !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~⌂ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■"""

  /** 单个 1 倍宽字形在屏幕上占用的宽度（像素）。 */
  def charRenderWidth: Int = charWidth / 2

  /** 字形在屏幕上占用的高度（像素）。 */
  def charRenderHeight: Int = charHeight / 2

  /**
   * 资源重载（或字体数据变化）时重建内部缓存。
   * 基类什么都不做；动态字体渲染器在这里重建图集。
   */
  def initialize(): Unit = {}

  /**
   * 预生成字形。
   *
   * 旧版在渲染显示列表前调用它，确保渲染过程中不会临时生成字形；
   * 1.21.1 里它的作用是提前把字形写进图集，避免首帧缺字。
   */
  def generateChars(chars: Array[Char]): Unit = {
    var i = 0
    while (i < chars.length) {
      glyph(chars(i))
      i += 1
    }
  }

  def generateChars(chars: Array[Int]): Unit = {
    var i = 0
    while (i < chars.length) {
      glyph(chars(i))
      i += 1
    }
  }

  /**
   * 绘制一整个文本缓冲（屏幕 / 机器人 / 无人机的内容区）。
   *
   * @param pose           当前变换；本方法内部会额外做 0.5 倍缩放，调用方只需负责位置与整体缩放
   * @param buffers        顶点缓冲来源（GUI 用 `guiGraphics.bufferSource()`）
   * @param buffer         待绘制的单元数据（字符 + 打包颜色）
   * @param viewportWidth  视口列数
   * @param viewportHeight 视口行数
   * @param light          打包光照值；GUI 里用 [TextureFontRenderer.fullBright]
   */
  def drawBuffer(pose: PoseStack,
                 buffers: MultiBufferSource,
                 buffer: TextBuffer,
                 viewportWidth: Int,
                 viewportHeight: Int,
                 light: Int = TextureFontRenderer.fullBright): Unit = {
    if (pose == null || buffers == null || buffer == null) return

    val columns = math.min(viewportWidth, buffer.width)
    val rows = math.min(viewportHeight, buffer.height)
    if (columns <= 0 || rows <= 0) return

    // 第一遍：确保视口内的字形都已经生成。新字形需要重新上传所属纹理页，
    // 所以必须先走完这一遍再上传，否则首帧会缺字。
    var y = 0
    while (y < rows) {
      val line = buffer.buffer(y)
      var x = 0
      while (x < columns) {
        val char = line(x)
        if (char != ' ' && char != 0) glyph(char)
        x += 1
      }
      y += 1
    }
    flushPendingUploads()

    val format = buffer.format
    pose.pushPose()
    pose.scale(0.5f, 0.5f, 1f)
    val entry = pose.last()

    // 背景：把同一行里颜色相同的相邻单元合并成一个四边形，减少四边形数量。
    val background = buffers.getBuffer(RenderType.textBackground())
    y = 0
    while (y < rows) {
      val line = buffer.color(y)
      var x = 0
      while (x < columns) {
        val color = PackedColor.unpackBackground(line(x), format)
        var end = x + 1
        while (end < columns && PackedColor.unpackBackground(line(end), format) == color) end += 1
        // 黑色背景跳过：与原实现一致，屏幕底色由边框贴图提供。
        if (color != 0) {
          drawRect(background, entry,
            x * charWidth, y * charHeight,
            end * charWidth, (y + 1) * charHeight,
            (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, light)
        }
        x = end
      }
      y += 1
    }

    // 前景：逐字形提交，颜色写进顶点。
    y = 0
    while (y < rows) {
      val line = buffer.buffer(y)
      val colors = buffer.color(y)
      var x = 0
      while (x < columns) {
        val char = line(x)
        if (char != ' ' && char != 0) {
          val target = glyph(char)
          if (target != null) {
            val location = textureLocation(target.page)
            if (location != null) {
              val color = PackedColor.unpackForeground(colors(x), format)
              drawGlyph(buffers.getBuffer(RenderType.text(location)), entry,
                x * charWidth, y * charHeight, target,
                (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255, light)
            }
          }
        }
        x += 1
      }
      y += 1
    }

    pose.popPose()
  }

  /**
   * 绘制一行字符串（行内不做换行）。
   *
   * @param color 0xRRGGBB 或 0xAARRGGBB；alpha 为 0 时按不透明处理，方便沿用旧版只传 RGB 的写法
   */
  def drawString(pose: PoseStack,
                 buffers: MultiBufferSource,
                 s: String,
                 x: Int,
                 y: Int,
                 color: Int,
                 light: Int): Unit = {
    if (pose == null || buffers == null || s == null || s.isEmpty) return

    val length = ExtendedUnicodeHelper.length(s)
    if (length <= 0) return

    // 同 drawBuffer：先确保字形都已生成并上传。
    var index = 0
    var offset = 0
    while (index < length) {
      val char = s.codePointAt(offset)
      if (char != ' ') glyph(char)
      offset = s.offsetByCodePoints(offset, 1)
      index += 1
    }
    flushPendingUploads()

    val alpha = {
      val a = (color >>> 24) & 0xFF
      if (a == 0) 255 else a
    }
    val red = (color >> 16) & 0xFF
    val green = (color >> 8) & 0xFF
    val blue = color & 0xFF

    pose.pushPose()
    pose.translate(x.toFloat, y.toFloat, 0f)
    pose.scale(0.5f, 0.5f, 1f)
    val entry = pose.last()

    index = 0
    offset = 0
    var tx = 0f
    while (index < length) {
      val char = s.codePointAt(offset)
      if (char != ' ') {
        val target = glyph(char)
        if (target != null) {
          val location = textureLocation(target.page)
          if (location != null) {
            drawGlyph(buffers.getBuffer(RenderType.text(location)), entry,
              tx, 0f, target, red, green, blue, alpha, light)
          }
        }
      }
      tx += charWidth
      offset = s.offsetByCodePoints(offset, 1)
      index += 1
    }

    pose.popPose()
  }

  // ----------------------------------------------------------------------- //
  // 子类实现
  // ----------------------------------------------------------------------- //

  /** 单个字形在纹理中的像素宽度（1 倍宽字形）。 */
  protected def charWidth: Int

  /** 字形在纹理中的像素高度。 */
  protected def charHeight: Int

  /** 每个纹理页的边长（像素）。静态字体贴图与动态图集都按正方形处理，默认 256。 */
  protected def textureSize: Int = 256

  /** 第 page 页的贴图位置；无效页返回 null，调用方会跳过该字形。 */
  protected def textureLocation(page: Int): ResourceLocation

  /**
   * 取字形在纹理页中的位置；若尚未生成则先生成。
   *
   * 返回 null 表示该码点没有可用字形，调用方跳过。
   */
  protected def glyph(char: Int): Glyph

  /** 字形绘制时的额外缩放；静态字体用它实现 `fontCharScale` 配置。 */
  protected def glyphScale: Float = 1f

  /** 有未上传的纹理改动时上传；默认没有（静态字体不需要）。 */
  protected def flushPendingUploads(): Unit = {}

  // ----------------------------------------------------------------------- //
  // 顶点提交
  // ----------------------------------------------------------------------- //

  /** 提交一个纯色四边形（背景用；`text_background` 的顶点格式没有 UV，不能写 setUv）。 */
  private def drawRect(consumer: VertexConsumer, entry: PoseStack.Pose,
                       x0: Float, y0: Float, x1: Float, y1: Float,
                       red: Int, green: Int, blue: Int, light: Int): Unit = {
    consumer.addVertex(entry, x0, y0, 0f).setColor(red, green, blue, 255).setLight(light)
    consumer.addVertex(entry, x0, y1, 0f).setColor(red, green, blue, 255).setLight(light)
    consumer.addVertex(entry, x1, y1, 0f).setColor(red, green, blue, 255).setLight(light)
    consumer.addVertex(entry, x1, y0, 0f).setColor(red, green, blue, 255).setLight(light)
  }

  /**
   * 提交一个字形四边形。
   *
   * 位置按 [glyphScale] 放大：旧版静态字体用 `fontCharScale` 让字形向两侧溢出，
   * 这里沿用同一套算式（scale 为 1 时与字形矩形完全重合）。
   */
  private def drawGlyph(consumer: VertexConsumer, entry: PoseStack.Pose,
                        tx: Float, ty: Float, target: Glyph,
                        red: Int, green: Int, blue: Int, alpha: Int, light: Int): Unit = {
    val scale = glyphScale
    val width = target.width
    val height = target.height
    val x0 = tx - (width * scale - width)
    val x1 = tx + width * scale
    val y0 = ty - (height * scale - height)
    val y1 = ty + height * scale
    val size = textureSize.toFloat
    val u0 = target.x / size
    val v0 = target.y / size
    val u1 = (target.x + target.width) / size
    val v1 = (target.y + target.height) / size
    consumer.addVertex(entry, x0, y0, 0f).setColor(red, green, blue, alpha).setUv(u0, v0).setLight(light)
    consumer.addVertex(entry, x0, y1, 0f).setColor(red, green, blue, alpha).setUv(u0, v1).setLight(light)
    consumer.addVertex(entry, x1, y1, 0f).setColor(red, green, blue, alpha).setUv(u1, v1).setLight(light)
    consumer.addVertex(entry, x1, y0, 0f).setColor(red, green, blue, alpha).setUv(u1, v0).setLight(light)
  }
}

object TextureFontRenderer {
  /** 全亮度光照值，等价于 `LightTexture.pack(15, 15)`。GUI 与自发光屏幕用它。 */
  val fullBright: Int = 0xF000F0

  /**
   * 字形在某个纹理页里的像素矩形（左上角为原点）。
   *
   * @param page   纹理页索引，对应 `textureLocation`
   * @param width  像素宽度；2 倍宽字形是 1 倍宽的两倍
   * @param height 像素高度，等于字形高度
   */
  final case class Glyph(page: Int, x: Int, y: Int, width: Int, height: Int)
}
