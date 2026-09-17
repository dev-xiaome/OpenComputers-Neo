package li.cil.oc.client.gui.widget

import li.cil.oc.client.Textures
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/**
 * 水平 / 垂直进度条组件（原 1.7.10 的 `li.cil.oc.client.gui.widget.ProgressBar`）。
 *
 * ==1.21.1 迁移要点==
 *  - 原实现用 `Tessellator` 画一个带 UV 的四边形，UV 为 `(0,0)-(level,1)`、
 *    宽度为 `width * level`；1.21.1 改为一次
 *    [[net.minecraft.client.gui.GuiGraphics.blit]]，语义完全等价：
 *    取整张贴图的左上角 `level` 比例区域，按同样的比例铺开。
 *  - `zLevel` 无法再传给顶点（1.21.1 的 z 序由 `GuiGraphics` 的 blitOffset 管理），
 *    这里省略，绘制顺序仍由调用时机决定。
 *  - [[barTexture]] 显式声明为 [[net.minecraft.resources.ResourceLocation]]
 *    （原实现是推断类型），子类（如打印机界面）可以继续覆写它换贴图。
 */
class ProgressBar(val x: Int, val y: Int) extends Widget {
  override def width: Int = 140

  override def height: Int = 12

  def barTexture: ResourceLocation = Textures.guiBar

  var level = 0.0

  override def draw(guiGraphics: GuiGraphics): Unit = {
    if (level > 0) {
      val tx = owner.windowX + x
      val ty = owner.windowY + y
      // 源区域宽度（贴图像素）= 目标宽度，因此这里同时充当 UV 与尺寸。
      val w = math.min((width * math.min(level, 1.0)).toInt, width)
      if (w > 0) {
        guiGraphics.blit(barTexture, tx, ty, 0f, 0f, w, height, width, height)
      }
    }
  }
}
