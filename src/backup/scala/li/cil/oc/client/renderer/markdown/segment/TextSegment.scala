package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.Document
import net.minecraft.client.gui.{Font, GuiGraphics}

import scala.collection.mutable
import scala.util.matching.Regex

/**
 * 一段普通文本（Markdown 解析的起点）。
 *
 * 1.21.1 里原来那套 `GL11.glPushMatrix / glTranslatef / glScalef` 的「缩放后再按原坐标
 * 画字」手法，改为操作 `guiGraphics.pose()`：先平移到片段原点、按解析出的缩放系数
 * 缩放、再平移回去，这样 `drawString` 仍然可以按绝对坐标书写。
 */
private[markdown] class TextSegment(val parent: Segment, val text: String) extends BasicTextSegment {
  override def render(guiGraphics: GuiGraphics, x: Int, y: Int, indent: Int, maxWidth: Int,
                      renderer: Font, mouseX: Int, mouseY: Int): Option[InteractiveSegment] = {
    var currentX = x + indent
    var currentY = y
    var chars = text
    if (indent == 0) chars = chars.dropWhile(_.isWhitespace)
    val wrapIndent = computeWrapIndent(renderer)
    var numChars = maxChars(chars, maxWidth - indent, maxWidth - wrapIndent, renderer)
    var hovered: Option[InteractiveSegment] = None
    while (chars.length > 0) {
      val part = chars.take(numChars)
      hovered = hovered.orElse(resolvedInteractive.fold(None: Option[InteractiveSegment])(_.checkHovered(
        mouseX, mouseY, currentX, currentY,
        stringWidth(part, renderer), (Document.lineHeight(renderer) * resolvedScale).toInt)))
      val pose = guiGraphics.pose()
      pose.pushPose()
      pose.translate(currentX.toFloat, currentY.toFloat, 0f)
      pose.scale(resolvedScale, resolvedScale, 1f)
      pose.translate(-currentX.toFloat, -currentY.toFloat, 0f)
      guiGraphics.drawString(renderer, resolvedFormat + part, currentX, currentY, resolvedColor)
      pose.popPose()
      currentX = x + wrapIndent
      currentY += lineHeight(renderer)
      chars = chars.drop(numChars).dropWhile(_.isWhitespace)
      numChars = maxChars(chars, maxWidth - wrapIndent, maxWidth - wrapIndent, renderer)
    }

    hovered
  }

  override def refine(pattern: Regex, factory: (Segment, Regex.Match) => Segment): Iterable[Segment] = {
    val result = mutable.Buffer.empty[Segment]

    // 记录上一次匹配的结束位置，用来生成中间的纯文本片段。
    var textStart = 0
    for (m <- pattern.findAllMatchIn(text)) {
      // 匹配前的普通文本。
      if (m.start > textStart) {
        result += new TextSegment(this, text.substring(textStart, m.start))
      }
      textStart = m.end

      // 匹配到的格式化文本。
      result += factory(this, m)
    }

    // 匹配后的剩余文本。
    if (textStart == 0) {
      result += this
    }
    else if (textStart < text.length) {
      result += new TextSegment(this, text.substring(textStart))
    }
    result
  }

  // ----------------------------------------------------------------------- //

  override protected def lineHeight(renderer: Font): Int = (super.lineHeight(renderer) * resolvedScale).toInt

  override protected def stringWidth(s: String, renderer: Font): Int = (renderer.width(resolvedFormat + s) * resolvedScale).toInt

  // ----------------------------------------------------------------------- //

  protected def color: Option[Int] = None

  protected def scale: Option[Float] = None

  protected def format: String = ""

  private def resolvedColor: Int = color.getOrElse(parent match {
    case segment: TextSegment => segment.resolvedColor
    case _ => 0xDDDDDD
  })

  private def resolvedScale: Float = parent match {
    case segment: TextSegment => scale.getOrElse(1f) * segment.resolvedScale
    case _ => 1f
  }

  private def resolvedFormat: String = parent match {
    case segment: TextSegment => segment.resolvedFormat + format
    case _ => format
  }

  private lazy val resolvedInteractive: Option[InteractiveSegment] = this match {
    case segment: InteractiveSegment => Some(segment)
    case _ => parent match {
      case segment: TextSegment => segment.resolvedInteractive
      case _ => None
    }
  }
}
