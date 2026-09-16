package li.cil.oc.client.gui.widget

import net.minecraft.client.gui.GuiGraphics

/**
 * GUI 小组件基类（原 1.7.10 的 `li.cil.oc.client.gui.widget.Widget`）。
 *
 * ==1.21.1 迁移要点==
 *  - 绘制入口由无参的 `draw()` 改为 [[draw(GuiGraphics)]]：1.21.1 的 GUI 绘制统一走
 *    [[net.minecraft.client.gui.GuiGraphics]]，固定管线的 `Tessellator` + `GL11` 已经不可用，
 *    因此绘制上下文必须显式传递。
 *  - 坐标语义不变：`x` / `y` 是相对宿主窗口左上角（[[WidgetContainer.windowX]] /
 *    [[WidgetContainer.windowY]]）的偏移，具体平移由子类在 [[draw]] 里自己做。
 *  - `owner` 由 [[WidgetContainer.addWidget]] 赋值；只有被添加到容器后的组件才会被绘制。
 */
abstract class Widget {
  var owner: WidgetContainer = _

  def x: Int

  def y: Int

  def width: Int

  def height: Int

  def draw(guiGraphics: GuiGraphics): Unit
}
