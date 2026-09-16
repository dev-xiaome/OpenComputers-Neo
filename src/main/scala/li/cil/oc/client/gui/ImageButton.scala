package li.cil.oc.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * 贴图按钮（原 1.7.10 的 `li.cil.oc.client.gui.ImageButton`）。
 *
 * ==1.21.1 迁移要点==
 *  - 基类由 `GuiButton` 换成 [[net.minecraft.client.gui.components.AbstractWidget]]
 *    （1.21.1 没有 `GuiButton`）。构造器保留 1.7.10 的形状，第一个参数 `id` 现在是本类自己的
 *    成员（原 `GuiButton#id`），供界面在回调里区分按钮；`text` 转成 [[Component]]。
 *  - 绘制入口 `drawButton(mc, mouseX, mouseY)` → [[renderWidget]]；
 *    贴图绘制由 `Tessellator` + UV 改为 [[net.minecraft.client.gui.GuiGraphics.blit]]，
 *    按「贴图被 2x2 均分」的约定选 UV：
 *    左半 / 右半 = 未切换 / 已切换（仅 [[canToggle]] 为真时使用右半），
 *    上半 / 下半 = 普通 / 悬停。
 *  - 屏幕侧注册方式：`Screen#addRenderableWidget(button)`
 *    （1.7.10 是把自己塞进 `GuiScreen#buttonList`）。
 *  - 点击回调：原 `actionPerformed(GuiButton)` → [[actionPerformed(ImageButton)]]
 *    （参数就是按钮自身，与原实现一致）。也仍然可以覆写 `AbstractWidget#onClick`。
 *  - `enabled` / `visible` / `active` 的兼容：`visible` 直接沿用父类的公开字段
 *    （`button.visible = false` 依旧可写），`enabled` 则转发到父类的 `active` 字段。
 */
class ImageButton(val id: Int, x: Int, y: Int, w: Int, h: Int,
                  val image: ResourceLocation = null,
                  text: String = null,
                  val canToggle: Boolean = false,
                  val textColor: Int = 0xE0E0E0,
                  val textDisabledColor: Int = 0xA0A0A0,
                  val textHoverColor: Int = 0xFFFFA0,
                  val textIndent: Int = -1)
  extends AbstractWidget(x, y, w, h, if (text == null) Component.empty() else Component.literal(text)) {

  /** 按钮是否处于「已切换」状态（配合 [[canToggle]] 使用贴图的右半）。 */
  var toggled = false

  /** 强制按悬停状态绘制（原实现用于滚动按钮拖拽时的视觉反馈）。 */
  var hoverOverride = false

  /** 这个按钮当前是否被鼠标悬停（原 `GuiButton#func_146115_a`）。 */
  var hoveredState = false

  // ----------------------------------------------------------------------- //
  // 1.7.10 兼容访问器
  // ----------------------------------------------------------------------- //

  def xPosition: Int = getX

  def xPosition_=(value: Int): Unit = setX(value)

  def yPosition: Int = getY

  def yPosition_=(value: Int): Unit = setY(value)

  /** 原 `GuiButton#enabled`（1.21.1 的对应物是 `AbstractWidget#active`）。 */
  def enabled: Boolean = active

  def enabled_=(value: Boolean): Unit = active = value

  /** 原 `GuiButton#displayString`（1.21.1 的文字放在 `Component` 里）。 */
  def displayString: String = getMessage.getString

  def displayString_=(value: String): Unit =
    setMessage(if (value == null) Component.empty() else Component.literal(value))

  /** 原 `GuiButton#func_146115_a`（是否为悬停状态）。 */
  def func_146115_a: Boolean = hoveredState

  // ----------------------------------------------------------------------- //
  // 绘制
  // ----------------------------------------------------------------------- //

  override protected def renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    val x0 = getX
    val y0 = getY
    val x1 = x0 + getWidth
    val y1 = y0 + getHeight

    hoveredState = mouseX >= x0 && mouseY >= y0 && mouseX < x1 && mouseY < y1
    val isHovered = hoverOverride || hoveredState

    // 贴图按 2x2 均分处理，因此统一用 2x2 作为「贴图尺寸」，
    // 源区域用整数像素坐标 (0..1) 表达 0 / 0.5 的 UV 分界。
    val texSize = 2
    if (image != null) {
      if (canToggle) {
        // 左半 = 未切换，右半 = 已切换；上下半 = 普通 / 悬停。
        guiGraphics.blit(image, x0, y0, getWidth, getHeight,
          if (toggled) 1f else 0f, if (isHovered) 1f else 0f, 1, 1, texSize, texSize)
      }
      else {
        // 整宽贴图，下半 = 悬停。
        guiGraphics.blit(image, x0, y0, getWidth, getHeight,
          0f, if (isHovered) 1f else 0f, texSize, 1, texSize, texSize)
      }
    }
    else {
      // 无贴图的按钮：原实现用半透明白色填充（悬停 0.8 / 普通 0.4）。
      guiGraphics.fill(x0, y0, x1, y1, if (isHovered) 0xCCFFFFFF else 0x66FFFFFF)
    }

    if (!getMessage.getString.isEmpty) {
      val color =
        if (!active) textDisabledColor
        else if (isHovered) textHoverColor
        else textColor
      val ty = y0 + (getHeight - 8) / 2
      if (textIndent >= 0) guiGraphics.drawString(Minecraft.getInstance.font, getMessage, x0 + textIndent, ty, color, true)
      else guiGraphics.drawCenteredString(Minecraft.getInstance.font, getMessage, x0 + getWidth / 2, ty, color)
    }
  }

  override protected def updateWidgetNarration(output: NarrationElementOutput): Unit = {
    defaultButtonNarrationText(output)
  }

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  /** 左右键都算有效点击（与 1.7.10 的 `GuiButton` 行为一致）。 */
  override protected def isValidClickButton(button: Int): Boolean = button == 0 || button == 1

  override def mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    val handled = super.mouseClicked(mouseX, mouseY, button)
    if (handled) actionPerformed(this)
    handled
  }

  /** 原 1.7.10 `GuiButton#actionPerformed` 的等价回调。 */
  protected def actionPerformed(button: ImageButton): Unit = ()
}
