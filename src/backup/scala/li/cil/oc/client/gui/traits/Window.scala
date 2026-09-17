package li.cil.oc.client.gui.traits

import java.util

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceLocation

/**
 * 「一张固定尺寸的窗口底图 + 居中的 GUI 坐标」混入
 * （原 1.7.10 的 `li.cil.oc.client.gui.traits.Window`，非容器界面用）。
 *
 * ==1.21.1 迁移要点==
 *  - `initGui()` → `init()`：原实现绕了一圈 `ScaledResolution` 来算居中位置，
 *    实际上 `ScaledResolution(windowWidth, windowHeight)` 的缩放系数恒为 1
 *    （176x166 小于 320x240 的阈值），所以 `xSize` / `ySize` 就等于
 *    [[windowWidth]] / [[windowHeight]]，居中直接用 `(width - xSize) / 2` 即可；
 *  - `doesGuiPauseGame` → [[isPauseScreen]]；
 *  - `bindTexture` + `Gui.func_146110_a` → [[GuiGraphics#blit]]；
 *  - `drawScreen` → [[render]]。
 *
 * 注意：这里的 `guiLeft` / `guiTop` / `xSize` / `ySize` 是给**普通界面**用的；
 * 容器界面请用 `AbstractContainerScreen` 的 `leftPos` / `topPos` /
 * `imageWidth` / `imageHeight`（见 [[li.cil.oc.client.gui.CustomGuiContainer]]）。
 */
trait Window extends Screen {
  var guiLeft = 0

  var guiTop = 0

  var xSize = 0

  var ySize = 0

  val windowWidth = 176

  val windowHeight = 166

  def backgroundImage: ResourceLocation

  /** 1.7.10 的 `add(list, value)` 小工具（原实现里到处在用）。 */
  protected def add[T](list: util.List[T], value: Any): Boolean = list.add(value.asInstanceOf[T])

  override def isPauseScreen: Boolean = false

  override protected def init(): Unit = {
    super.init()

    xSize = windowWidth
    ySize = windowHeight
    guiLeft = (width - xSize) / 2
    guiTop = (height - ySize) / 2
  }

  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    // 原 1.7.10：bindTexture(backgroundImage) + Gui.func_146110_a(guiLeft, guiTop, 0, 0, xSize, ySize, windowWidth, windowHeight)
    guiGraphics.blit(backgroundImage, guiLeft, guiTop, 0, 0, xSize, ySize, windowWidth, windowHeight)

    super.render(guiGraphics, mouseX, mouseY, partialTick)
  }
}
