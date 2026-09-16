package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.gui.{Font, GuiGraphics}

/**
 * 行内代码片段（Markdown 的 `` `...` ``）。
 *
 * ==降级说明==
 * 1.7.10 里这段文字是用 OpenComputers 自己的等宽位图字体
 * （`TextBufferRenderCache.renderer`，即 `DynamicFontRenderer`）绘制的：
 * `renderer.drawString(part, x, y)` 直接往 GL 里写顶点，宽度按
 * `renderer.charRenderWidth` 计算，因此能用等宽字体显示代码。
 *
 * 1.21.1 的 `DynamicFontRenderer` 需要走 `PoseStack` + `VertexConsumer`，
 * 与 `GuiGraphics` 的批次不是同一条管线，混用会破坏 GUI 的绘制顺序与裁剪。
 * 因此这里暂时用原版字体绘制，并保留代码块的浅蓝配色。
 *
 * TODO(manual): 等 `client/renderer/font/DynamicFontRenderer` 给出
 *   「基于 `GuiGraphics` 的绘制入口」后，把下面两处替换掉：
 *     - `guiGraphics.drawString(...)` → 等宽字体绘制；
 *     - `renderer.width(s)` → 等宽字体的字符宽度 × 字符数。
 */
private[markdown] class CodeSegment(val parent: Segment, val text: String) extends BasicTextSegment {
  override def render(guiGraphics: GuiGraphics, x: Int, y: Int, indent: Int, maxWidth: Int,
                      renderer: Font, mouseX: Int, mouseY: Int): Option[InteractiveSegment] = {
    var currentX = x + indent
    var currentY = y
    var chars = text
    val wrapIndent = computeWrapIndent(renderer)
    var numChars = maxChars(chars, maxWidth - indent, maxWidth - wrapIndent, renderer)
    while (chars.length > 0) {
      val part = chars.take(numChars)
      guiGraphics.drawString(renderer, part, currentX, currentY, CodeSegment.codeColor)
      currentX = x + wrapIndent
      currentY += lineHeight(renderer)
      chars = chars.drop(numChars).dropWhile(_.isWhitespace)
      numChars = maxChars(chars, maxWidth - wrapIndent, maxWidth - wrapIndent, renderer)
    }

    None
  }

  override protected def ignoreLeadingWhitespace: Boolean = false

  // 1.7.10 用等宽字体的固定字符宽度；这里退回原版字体的实测宽度。
  override protected def stringWidth(s: String, renderer: Font): Int = renderer.width(s)

  override def toString(format: MarkupFormat.Value): String = format match {
    case MarkupFormat.Markdown => s"`$text`"
    case MarkupFormat.IGWMod => s"[prefix{1}]$text [prefix{}]"
  }
}

private[markdown] object CodeSegment {
  /** 代码块的文字颜色（对应 1.7.10 里 `GL11.glColor4f(0.75f, 0.8f, 1, 1)`）。 */
  final val codeColor = 0xBFC0FF
}
