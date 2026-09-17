package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.{ImageProvider, ImageRenderer, InteractiveImageRenderer}
import li.cil.oc.client.Textures

/**
 * 手册图片前缀 `item`：按物品注册名取图标。
 *
 * 1.7.10 用 `Item.itemRegistry.getObject(name)` 查物品；1.21.1 改成
 * `BuiltInRegistries.ITEM`，命名空间替换与子类型处理见 [[ManualImages]]。
 */
object ItemImageProvider extends ImageProvider {
  override def getImage(data: String): ImageRenderer = {
    val stack = ManualImages.stack(data)
    if (!stack.isEmpty) new ItemStackImageRenderer(Array(stack))
    else new TextureImageRenderer(Textures.guiManualMissingItem) with InteractiveImageRenderer {
      override def getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.ItemMissing"

      override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
    }
  }
}
