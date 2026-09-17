package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.{ImageProvider, ImageRenderer, InteractiveImageRenderer}
import li.cil.oc.client.Textures

/**
 * 手册图片前缀 `texture`：直接把一整张贴图画进页面。
 *
 * 1.7.10 用 `new ResourceLocation(data)`；1.21.1 改成 `ResourceLocation.tryParse`
 * （非法名字不再抛异常，而是走「缺失」占位图），并沿用 [[ManualImages]] 的命名空间替换。
 */
object TextureImageProvider extends ImageProvider {
  override def getImage(data: String): ImageRenderer = {
    val location = ManualImages.location(data)
    if (location != null) new TextureImageRenderer(location)
    else new TextureImageRenderer(Textures.guiManualMissingItem) with InteractiveImageRenderer {
      override def getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.ImageMissing"

      override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
    }
  }
}
