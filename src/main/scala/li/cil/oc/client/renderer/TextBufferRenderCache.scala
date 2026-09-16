package li.cil.oc.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.Settings
import li.cil.oc.client.renderer.font.{TextBufferRenderData, TextureFontRenderer}
import li.cil.oc.util.TextBuffer
import net.minecraft.client.renderer.MultiBufferSource

/**
 * 文本缓冲的渲染入口：挑选字体渲染器，把 [[TextBuffer]] 的内容交给它画出来。
 *
 * ==1.7.10 到 1.21.1 的变化==
 * 旧版用 Guava 缓存把每个缓冲编译成一张 OpenGL 显示列表
 * （`GLAllocation.generateDisplayLists` / `glNewList` / `glCallList`），
 * 并在客户端 tick 时 `cache.cleanUp()` 回收过期的显示列表。
 * 1.21.1 已经彻底移除固定管线的显示列表：
 *  - 每个缓冲不再持有任何 GPU 资源，字形图集由字体渲染器全局共享
 *    （见 [[li.cil.oc.client.renderer.font.DynamicFontRenderer]]），
 *    所以这里不再需要按缓冲缓存，也不需要 RemovalListener / tick 清理；
 *  - 顶点改成每帧写进 `MultiBufferSource`，由调用方在帧末统一提交；
 *  - `TextBufferRenderData#dirty` 的语义退化为「内容有变化」，渲染器自己按码点缓存字形，
 *    因此这里不再读取 dirty（保留 trait 是为了让 `gui.Drone` 等调用点少改）。
 *
 * ==接线==
 * 由 `client.Proxy` 在客户端初始化时调用一次 [initialize]：
 *  - `DynamicFontRenderer` 在这里解析 `font.hex` 并重建字形图集；
 *  - `StaticFontRenderer` 在这里重读 `chars.txt`。
 */
object TextBufferRenderCache {
  /** 与旧版一致：配置里 `client.fontRenderer = "texture"` 时用静态贴图字体。 */
  val renderer: TextureFontRenderer =
    if (Settings.get.fontRenderer == "texture") new font.StaticFontRenderer()
    else new font.DynamicFontRenderer()

  /** 资源重载 / 客户端初始化入口。可重复调用，幂等。 */
  def initialize(): Unit = renderer.initialize()

  /**
   * 绘制一个 [[TextBufferRenderData]]。
   *
   * 调用方负责先 `pushPose` 并平移到内容区左上角（整体缩放也由调用方给）。
   */
  def render(pose: PoseStack,
             buffers: MultiBufferSource,
             buffer: TextBufferRenderData): Unit = {
    if (buffer == null) return
    render(pose, buffers, buffer.data, buffer.viewport._1, buffer.viewport._2, TextureFontRenderer.fullBright)
  }

  /**
   * 直接绘制一个 [[TextBuffer]]（`common.component.TextBuffer` 的底层数据）。
   *
   * @param viewportWidth  视口列数
   * @param viewportHeight 视口行数
   */
  def render(pose: PoseStack,
             buffers: MultiBufferSource,
             buffer: TextBuffer,
             viewportWidth: Int,
             viewportHeight: Int,
             light: Int = TextureFontRenderer.fullBright): Unit =
    renderer.drawBuffer(pose, buffers, buffer, viewportWidth, viewportHeight, light)
}
