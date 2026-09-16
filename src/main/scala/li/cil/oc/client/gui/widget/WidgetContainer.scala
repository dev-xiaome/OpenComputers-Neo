package li.cil.oc.client.gui.widget

import net.minecraft.client.gui.GuiGraphics

import scala.collection.mutable

/**
 * 小组件容器（原 1.7.10 的 `li.cil.oc.client.gui.widget.WidgetContainer`）。
 *
 * ==1.21.1 迁移要点==
 *  - [[drawWidgets]] 增加 [[net.minecraft.client.gui.GuiGraphics]] 参数（原为无参），
 *    与 [[Widget.draw]] 的签名改动配套；调用点见 `DynamicGuiContainer` 的渲染流程。
 *  - `windowX` / `windowY` 改为显式返回 `Int`（原来是推断类型），
 *    避免在混入 Java 泛型基类时被推断成奇怪的返回类型。
 *  - `windowZ` 保留为 `Float`，但 1.21.1 的 z 序由 `GuiGraphics` 内部管理，
 *    它只是给子类传值用的兼容成员。
 */
trait WidgetContainer {
  protected val widgets = mutable.ArrayBuffer.empty[Widget]

  def addWidgetToContainer[T <: Widget](widget: T): T = {
    widgets += widget
    widget.owner = this
    widget
  }

  def windowX: Int = 0

  def windowY: Int = 0

  def windowZ: Float = 0f

  def drawWidgets(guiGraphics: GuiGraphics): Unit = {
    widgets.foreach(_.draw(guiGraphics))
  }
}
