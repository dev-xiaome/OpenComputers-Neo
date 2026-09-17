package li.cil.oc.client.renderer.markdown

import li.cil.oc.api
import li.cil.oc.client.renderer.markdown.segment.{InteractiveSegment, Segment}
import net.minecraft.client.gui.{Font, GuiGraphics}

import scala.collection.Iterable
import scala.util.matching.Regex

/**
 * 极简 Markdown 解析器，只支持很小的一个子集，用于把手册页面文本解析成片段
 * 后交给 GUI 绘制。
 *
 * 一般用法：用 `parse` 解析文本，用 `render` 绘制。
 *
 * 解析结果是「一串片段」，每个片段代表文档的一部分，可能带有特定格式/渲染方式。
 * 例如链接是独立片段，链接里的粗体又是它自己的片段，以此类推。
 * 数据结构本质上是一棵很扁的树：返回的片段是叶子，根是每一行（用文本片段表示）。
 * 格式化信息沿着父链一路累积到根。
 *
 * ==1.21.1 迁移要点==
 *  - 立即模式 `GL11` 全部删除，绘制改为 `GuiGraphics`；
 *  - 1.7.10 里用「深度缓冲 + 颜色掩码」在滚动区域上下各画一块遮罩的技巧，
 *    在 1.21.1 中改由 [GuiGraphics#enableScissor] 做裁剪（更简单也更可靠）；
 *  - 调用方传入的 `(x, y)` 现在是**屏幕绝对坐标**，函数内部会
 *    `pose.translate(x, y, 0)`，片段自身仍按「相对文档左上角」的坐标绘制，
 *    因此鼠标坐标也要相应减去 `(x, y)`。
 */
object Document {
  /** 解析一份纯文本文档，返回片段链表的头。 */
  def parse(document: Iterable[String]): Segment = {
    var segments: Iterable[Segment] = document.map(line => new segment.TextSegment(null, Option(line).fold("")(_.reverse.dropWhile(_.isWhitespace).reverse)))
    for ((pattern, factory) <- segmentTypes) {
      segments = segments.flatMap(_.refine(pattern, factory))
    }
    for (window <- segments.sliding(2) if window.size == 2) {
      window.head.next = window.last
    }
    segments.head
  }

  /** 计算整份文档的高度（用于滚动条 / 滚动偏移计算）。 */
  def height(document: Segment, maxWidth: Int, renderer: Font): Int = {
    var currentX = 0
    var currentY = 0
    var segment = document
    while (segment != null) {
      currentY += segment.nextY(currentX, maxWidth, renderer)
      currentX = segment.nextX(currentX, maxWidth, renderer)
      segment = segment.next
    }
    currentY
  }

  /** 普通文本行的行高。 */
  def lineHeight(renderer: Font): Int = renderer.lineHeight + 1

  /**
   * 绘制整份文档，返回鼠标悬停到的可交互片段（如果有）。
   *
   * @param guiGraphics 当前 GUI 的绘图上下文
   * @param x,y         文档区域左上角的屏幕绝对坐标
   * @param maxWidth    文档区域宽度
   * @param maxHeight   文档区域高度（超出部分被裁剪）
   * @param yOffset     滚动偏移
   * @param mouseX,mouseY 鼠标的屏幕绝对坐标
   */
  def render(guiGraphics: GuiGraphics,
             document: Segment,
             x: Int, y: Int,
             maxWidth: Int, maxHeight: Int,
             yOffset: Int,
             renderer: Font,
             mouseX: Int, mouseY: Int): Option[InteractiveSegment] = {
    val pose = guiGraphics.pose()
    val previousContext = RenderContext.push(guiGraphics)

    // 裁剪到文档区域，替代 1.7.10 那套深度缓冲遮罩。
    guiGraphics.enableScissor(x, y, x + maxWidth, y + maxHeight)
    pose.pushPose()
    pose.translate(x.toFloat, y.toFloat, 0f)

    // 片段坐标以文档左上角为原点。
    val localMouseX = mouseX - x
    val localMouseY = mouseY - y

    var hovered: Option[InteractiveSegment] = None
    var indent = 0
    var currentY = -yOffset
    val minY = -lineHeight(renderer)
    val maxY = maxHeight + lineHeight(renderer)
    var segment = document
    while (segment != null) {
      val segmentHeight = segment.nextY(indent, maxWidth, renderer)
      if (currentY + segmentHeight >= minY && currentY <= maxY) {
        val result = segment.render(guiGraphics, 0, currentY, indent, maxWidth, renderer, localMouseX, localMouseY)
        hovered = hovered.orElse(result)
      }
      currentY += segmentHeight
      indent = segment.nextX(indent, maxWidth, renderer)
      segment = segment.next
    }
    if (mouseX < x || mouseX > x + maxWidth || mouseY < y || mouseY > y + maxHeight) hovered = None
    hovered.foreach(_.notifyHover())

    pose.popPose()
    guiGraphics.disableScissor()
    RenderContext.pop(previousContext)

    hovered
  }

  // ----------------------------------------------------------------------- //

  private def HeaderSegment(s: Segment, m: Regex.Match) = new segment.HeaderSegment(s, m.group(2), m.group(1).length)

  private def CodeSegment(s: Segment, m: Regex.Match) = new segment.CodeSegment(s, m.group(2))

  private def LinkSegment(s: Segment, m: Regex.Match) = new segment.LinkSegment(s, m.group(1), m.group(2))

  private def BoldSegment(s: Segment, m: Regex.Match) = new segment.BoldSegment(s, m.group(2))

  private def ItalicSegment(s: Segment, m: Regex.Match) = new segment.ItalicSegment(s, m.group(2))

  private def StrikethroughSegment(s: Segment, m: Regex.Match) = new segment.StrikethroughSegment(s, m.group(1))

  private def ImageSegment(s: Segment, m: Regex.Match) = {
    try Option(api.Manual.imageFor(m.group(2))) match {
      case Some(renderer) => new segment.RenderSegment(s, m.group(1), renderer)
      case _ => new segment.TextSegment(s, "No renderer found for: " + m.group(2))
    } catch {
      case t: Throwable => new segment.TextSegment(s, Option(t.toString).getOrElse("Unknown error."))
    }
  }

  // ----------------------------------------------------------------------- //

  private val segmentTypes = Array(
    """^(#+)\s(.*)""".r -> HeaderSegment _, // 标题：# ...
    """(`)(.*?)\1""".r -> CodeSegment _, // 行内代码：`...`
    """!\[([^\[]*)\]\(([^\)]+)\)""".r -> ImageSegment _, // 图片：![...](...)
    """\[([^\[]+)\]\(([^\)]+)\)""".r -> LinkSegment _, // 链接：[...](...)
    """(\*\*|__)(\S.*?\S|$)\1""".r -> BoldSegment _, // 粗体：**...** | __...__
    """(\*|_)(\S.*?\S|$)\1""".r -> ItalicSegment _, // 斜体：*...* | _..._
    """~~(\S.*?\S|$)~~""".r -> StrikethroughSegment _ // 删除线：~~...~~
  )
}
