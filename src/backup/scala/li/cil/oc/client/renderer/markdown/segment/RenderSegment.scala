package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.api.manual.{ImageRenderer, InteractiveImageRenderer}
import li.cil.oc.client.renderer.markdown.Document
import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.gui.{Font, GuiGraphics}

/**
 * 图片片段（Markdown 的 `![标题](图片 URL)`）。
 *
 * 图片本身由 `li.cil.oc.api.manual.ImageRenderer` 负责绘制。由于那个接口是**只读的
 * API 层 Java 接口**（`render(mouseX, mouseY)`，立刻模式），1.21.1 下借助
 * [[li.cil.oc.client.renderer.markdown.RenderContext]] 把当前 `GuiGraphics` 传进去，
 * 并用 `PoseStack` 把「平移 + 缩放」铺好，使图片渲染器仍可以按 `(0,0)` 起画。
 */
private[markdown] class RenderSegment(val parent: Segment, val title: String, val imageRenderer: ImageRenderer) extends InteractiveSegment {
  var lastX = 0
  var lastY = 0

  override def tooltip: Option[String] = imageRenderer match {
    case interactive: InteractiveImageRenderer => Option(interactive.getTooltip(title))
    case _ => Option(title)
  }

  override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = imageRenderer match {
    case interactive: InteractiveImageRenderer => interactive.onMouseClick(mouseX - lastX, mouseY - lastY)
    case _ => false
  }

  private def scale(maxWidth: Int): Float = math.min(1f, maxWidth / imageRenderer.getWidth.toFloat)

  def imageWidth(maxWidth: Int): Int = math.min(maxWidth, imageRenderer.getWidth)

  def imageHeight(maxWidth: Int): Int = math.ceil(imageRenderer.getHeight * scale(maxWidth)).toInt + 4

  override def nextY(indent: Int, maxWidth: Int, renderer: Font): Int =
    imageHeight(maxWidth) + (if (indent > 0) Document.lineHeight(renderer) else 0)

  override def nextX(indent: Int, maxWidth: Int, renderer: Font): Int = 0

  override def render(guiGraphics: GuiGraphics, x: Int, y: Int, indent: Int, maxWidth: Int,
                      renderer: Font, mouseX: Int, mouseY: Int): Option[InteractiveSegment] = {
    val width = imageWidth(maxWidth)
    val height = imageHeight(maxWidth)
    val xOffset = (maxWidth - width) / 2
    val yOffset = 2 + (if (indent > 0) Document.lineHeight(renderer) else 0)
    val s = scale(maxWidth)

    lastX = x + xOffset
    lastY = y + yOffset

    val hovered = checkHovered(mouseX, mouseY, x + xOffset, y + yOffset, width, height)

    val pose = guiGraphics.pose()
    pose.pushPose()
    pose.translate(lastX.toFloat, lastY.toFloat, 0f)
    pose.scale(s, s, 1f)

    if (hovered.isDefined) {
      // 悬停高亮：原来是 `GL11.glColor4f(1, 1, 1, 0.15f)` 下画一个纯色四边形。
      guiGraphics.fill(0, 0, imageRenderer.getWidth, imageRenderer.getHeight, 0x26FFFFFF)
    }

    // 图片渲染器从 `RenderContext` 里取当前 `GuiGraphics`，在 (0,0) 起画。
    imageRenderer.render(mouseX - x, mouseY - y)

    pose.popPose()

    hovered
  }

  override def toString(format: MarkupFormat.Value): String = format match {
    case MarkupFormat.Markdown => s"![$title]($imageRenderer)"
    case MarkupFormat.IGWMod => "(抱歉，图片目前只在 OpenComputers 手册里可用。)"
  }
}
