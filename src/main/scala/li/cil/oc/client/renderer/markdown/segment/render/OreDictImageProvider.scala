package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.{ImageProvider, ImageRenderer, InteractiveImageRenderer}
import li.cil.oc.client.Textures

/**
 * 手册图片前缀 `oredict`。
 *
 * ==降级说明==
 * 1.7.10 用 `OreDictionary.getOres(data)` 拿到「同一矿辞下的所有物品」，
 * 再轮播它们的图标。1.21.1 已经**没有 `OreDictionary`**，矿辞被物品 tag
 * （`TagKey<Item>`，`c:` 命名空间）取代，而 tag 里的物品需要通过
 * `BuiltInRegistries.ITEM.getTag(tagKey)` 动态取，且 tag 名与旧矿辞名
 * （`ingotIron` / `dustRedstone` …）之间没有一一对应关系，无法机械换算。
 *
 * 因此这里退化为「按物品名查一次图标」：手册页面里写
 * `oredict:opencomputers:chip1` 这类完整注册名时仍然可用，
 * 只写旧矿辞名时显示「缺失」占位图并给出 tooltip。
 *
 * TODO(manual): 等 `data/opencomputers_neo/tags/` 下的矿物 tag 补齐后，
 *   在这里按 `c:ingot_iron` 这类 tag 名轮播 `BuiltInRegistries.ITEM.getTag(...)` 的结果。
 */
object OreDictImageProvider extends ImageProvider {
  override def getImage(data: String): ImageRenderer = {
    val stack = ManualImages.stack(data)
    if (!stack.isEmpty) new ItemStackImageRenderer(Array(stack))
    else new TextureImageRenderer(Textures.guiManualMissingItem) with InteractiveImageRenderer {
      override def getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.OreDictMissing"

      override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
    }
  }
}
