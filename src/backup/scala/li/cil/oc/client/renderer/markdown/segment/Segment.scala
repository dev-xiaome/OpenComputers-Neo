package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.gui.{Font, GuiGraphics}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.matching.Regex

/**
 * 手册文档的一个片段（Markdown 解析结果的最小单元）。
 *
 * ==1.21.1 迁移要点==
 *  - `net.minecraft.client.gui.FontRenderer` 已被删除，字体统一用
 *    [[net.minecraft.client.gui.Font]]（`getStringWidth` → `width`、
 *    `FONT_HEIGHT` → `lineHeight`）。
 *  - 立即模式的 `GL11` 不复存在，所有绘制都要拿到一个 [[GuiGraphics]]，
 *    因此 [[render]] 的第一个参数改成 `GuiGraphics`；
 *    平移/缩放改为操作 `guiGraphics.pose()`。
 */
trait Segment {
  /**
   * 父片段，即本片段是从哪个片段细化出来的。
   * 每一行一开始都是一个 [[TextSegment]]，再按格式化规则细化成多个片段。
   */
  def parent: Segment

  /** 根片段，即本片段最初的父片段。 */
  @tailrec final def root: Segment = if (parent == null) this else parent.root

  /**
   * 下一个片段应该在哪个 X 坐标开始渲染（坐标相对文档左上角）。
   *
   * 对行内片段来说这是本片段最后一行右侧，对块级片段来说是下一行的行首。
   */
  def nextX(indent: Int, maxWidth: Int, renderer: Font): Int

  /**
   * 下一个片段应该在哪个 Y 坐标开始渲染（坐标相对文档左上角）。
   */
  def nextY(indent: Int, maxWidth: Int, renderer: Font): Int

  /** 在指定坐标渲染本片段，返回本次命中的可交互片段（如果有）。 */
  def render(guiGraphics: GuiGraphics, x: Int, y: Int, indent: Int, maxWidth: Int,
             renderer: Font, mouseX: Int, mouseY: Int): Option[InteractiveSegment] = None

  def renderAsText(format: MarkupFormat.Value): Iterable[String] = {
    var segment = this
    val result = mutable.Buffer.empty[String]
    val builder = mutable.StringBuilder.newBuilder
    while (segment != null) {
      builder.append(segment.toString(format))
      if (segment.isLast) {
        result += builder.toString()
        builder.clear()
      }
      segment = segment.next
    }
    result.toIterable
  }

  def toString(format: MarkupFormat.Value): String

  override def toString: String = toString(MarkupFormat.Markdown)

  // ----------------------------------------------------------------------- //

  /** 构造期使用：按正则把本片段再细化成若干子片段。 */
  private[markdown] def refine(pattern: Regex, factory: (Segment, Regex.Match) => Segment): Iterable[Segment] = Iterable(this)

  /** 文档构造完成后设置，用于判断「本片段是不是一行里的最后一个」。 */
  private[markdown] var next: Segment = null

  /** 本片段是否是所在行的最后一个片段。 */
  private[markdown] def isLast: Boolean = next == null || root != next.root
}
