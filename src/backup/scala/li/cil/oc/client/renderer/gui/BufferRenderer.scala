package li.cil.oc.client.renderer.gui

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.api
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.TextBufferRenderCache
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.TextureManager

/**
 * 屏幕 / 机器人 / 无人机 GUI 里的「内容区」渲染器：负责边框与缓冲内容。
 *
 * ==1.7.10 到 1.21.1 的变化==
 *  - 旧版 `init(TextureManager)` 只是把 GUI 贴图预绑定一遍（1.7.10 的 `bindTexture`
 *    顺带把贴图加载进显存），1.21.1 的贴图按需加载，因此 [init] 变成空实现，
 *    只保留签名让 `gui.traits.DisplayBuffer` 的调用点不用改形状。
 *  - 旧版 `compileBackground` 把九宫格边框编译成一张 OpenGL 显示列表，
 *    `drawBackground` 再 `glCallList`。1.21.1 没有显示列表，改成
 *    [compileBackground] 只记录尺寸、[drawBackground] 直接按记录绘制。
 *    边框走 `GuiGraphics#blit`（它内部立即提交，因此边框一定先于
 *    缓冲内容的顶点批次画出，层次关系与旧版一致）。
 *  - 旧版 `drawText` 只做深度遮罩开关，真正的绘制在
 *    `api.internal.TextBuffer#renderText` 内部（那里会调 `TextBufferRenderCache`）。
 *    1.21.1 的 `common.component.TextBuffer#renderText` 在 common 侧无法引用 client，
 *    因此改成由这里直接取底层单元数据并调用 [[TextBufferRenderCache]]。
 *
 * 边框贴图 `textures/gui/borders.png` 是 16x16 的九宫格：四角、四条边与中心各占一块。
 * 旧版把可拉伸的边与中心写成 `7.25 .. 8.75`（1.5 像素宽）来避免渗色；在 NEAREST 采样下
 * 等价于 `7 .. 9`，这里用整数 UV 区间，配合 `GuiGraphics#blit` 的浮点偏移做同样的拉伸。
 */
object BufferRenderer {
  /** 九宫格边框（非机器人）的角尺寸，与旧版一致。 */
  val margin = 7

  /** 内容区四周再留出的内边距（像素）。 */
  val innerMargin = 1

  /** `borders.png` 的边长（像素）。 */
  private val BorderTextureSize = 16

  // 最近一次 compileBackground 的参数；旧版这里是显示列表句柄。
  private var innerBufferWidth = 0

  private var innerBufferHeight = 0

  private var robotStyle = false

  private var prepared = false

  /**
   * 兼容旧调用点。
   *
   * TODO(渲染): 1.21.1 不需要预绑定贴图，这里刻意留空。
   */
  def init(tm: TextureManager): Unit = {}

  /** 记录边框尺寸。旧版在这里 `glNewList`，1.21.1 只是存下来。 */
  def compileBackground(bufferWidth: Int, bufferHeight: Int, forRobot: Boolean = false): Unit = {
    innerBufferWidth = bufferWidth
    innerBufferHeight = bufferHeight
    robotStyle = forRobot
    prepared = true
  }

  /** 用最近一次 [compileBackground] 的参数画边框。 */
  def drawBackground(guiGraphics: GuiGraphics): Unit = {
    if (prepared) drawBackground(guiGraphics, innerBufferWidth, innerBufferHeight, robotStyle)
  }

  /**
   * 画九宫格边框。
   *
   * @param bufferWidth  内容区宽度（不含 [innerMargin] 与边框）
   * @param bufferHeight 内容区高度
   * @param forRobot     机器人 / 无人机用小一号的边框
   */
  def drawBackground(guiGraphics: GuiGraphics,
                     bufferWidth: Int,
                     bufferHeight: Int,
                     forRobot: Boolean = false): Unit = {
    if (guiGraphics == null || bufferWidth <= 0 || bufferHeight <= 0) return

    val side = if (forRobot) 2 else margin
    // 旧版：(c0, c1, c2, c3) = (0, 7, 9, 16) 或 (5, 7, 9, 11)。
    val (c0, c1, c2, c3) = if (forRobot) (5, 7, 9, 11) else (0, 7, 9, 16)
    val leftWidth = c1 - c0
    val middleWidth = c2 - c1
    val rightWidth = c3 - c2
    val topHeight = c1 - c0
    val middleHeight = c2 - c1
    val bottomHeight = c3 - c2

    val width = innerMargin * 2 + bufferWidth
    val height = innerMargin * 2 + bufferHeight

    // 上边框：左角、可拉伸的横条、右角。
    drawBorder(guiGraphics, 0, 0, side, side, c0, c0, leftWidth, topHeight)
    drawBorder(guiGraphics, side, 0, width, side, c1, c0, middleWidth, topHeight)
    drawBorder(guiGraphics, side + width, 0, side, side, c2, c0, rightWidth, topHeight)

    // 中间：左竖条、内容区背景、右竖条。
    drawBorder(guiGraphics, 0, side, side, height, c0, c1, leftWidth, middleHeight)
    drawBorder(guiGraphics, side, side, width, height, c1, c1, middleWidth, middleHeight)
    drawBorder(guiGraphics, side + width, side, side, height, c2, c1, rightWidth, middleHeight)

    // 下边框：左角、可拉伸的横条、右角。
    drawBorder(guiGraphics, 0, side + height, side, side, c0, c2, leftWidth, bottomHeight)
    drawBorder(guiGraphics, side, side + height, width, side, c1, c2, middleWidth, bottomHeight)
    drawBorder(guiGraphics, side + width, side + height, side, side, c2, c2, rightWidth, bottomHeight)
  }

  /**
   * 绘制缓冲内容（背景色块 + 字形）。
   *
   * 调用方负责先 `pushPose` 并平移到内容区左上角、再做整体缩放（旧版同样如此）。
   *
   * ==降级说明==
   * 1.7.10 的 `api.internal.TextBuffer#renderText()` 会先把内容烘焙进当前线程的
   * 渲染数据，再由本对象取出单元数据交给字体图集绘制。1.21.1 里那条链路需要
   * `common.component.TextBuffer`（`common/component` 尚未进编译集）暴露底层
   * `util.TextBuffer`，`api.internal.TextBuffer` 接口并没有把它暴露出来，
   * 因此这里只能退化为直接调用接口上的 `renderText()`：
   *  - 它是接口方法，任何实现都能调，不会再出现「找不到成员」的编译错误；
   *  - 内置实现（common 侧）目前是占位，调用它不会画出内容；
   *  - 等 `common/component` 进编译集后，把这里换成
   *    `TextBufferRenderCache.render(pose, buffers, buffer.data, ...)` 即可恢复完整渲染。
   *
   * @return 是否真的绘制了内容（降级路径恒为 `false`）
   */
  def drawText(pose: PoseStack, buffers: MultiBufferSource, screen: api.internal.TextBuffer): Boolean = {
    if (pose == null || buffers == null || screen == null) return false

    // TODO(渲染): 等 `common.component.TextBuffer` 可用后，改为取 `data` + `viewport`
    // 交给 TextBufferRenderCache，从而真正画出字形。
    screen.renderText()
    false
  }

  /**
   * [GuiGraphics] 便捷重载：GUI 里通常只拿得到 `GuiGraphics`。
   */
  def drawText(guiGraphics: GuiGraphics, screen: api.internal.TextBuffer): Boolean = {
    if (guiGraphics == null) return false
    drawText(guiGraphics.pose(), guiGraphics.bufferSource(), screen)
  }

  /**
   * 提交一块九宫格贴图。
   *
   * 屏幕上的尺寸与贴图里的区域尺寸分开给，因此可拉伸的边与中心会被拉长。
   */
  private def drawBorder(guiGraphics: GuiGraphics,
                         x: Int, y: Int, width: Int, height: Int,
                         u: Int, v: Int, uWidth: Int, vHeight: Int): Unit = {
    if (width <= 0 || height <= 0 || uWidth <= 0 || vHeight <= 0) return
    guiGraphics.blit(Textures.guiBorders, x, y, width, height,
      u.toFloat, v.toFloat, uWidth, vHeight, BorderTextureSize, BorderTextureSize)
  }
}
