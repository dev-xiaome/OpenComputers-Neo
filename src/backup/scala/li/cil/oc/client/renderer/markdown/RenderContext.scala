package li.cil.oc.client.renderer.markdown

import net.minecraft.client.gui.GuiGraphics

/**
 * 手册渲染期间的「当前绘图上下文」。
 *
 * ==为什么需要它==
 * 1.7.10 的画图是立即模式：`GL11` 的状态与矩阵就是全局的，所以
 * `api.manual.ImageRenderer#render(mouseX, mouseY)` 只需要拿到鼠标坐标，
 * 平移/缩放全由调用方提前用 `glTranslatef/glScalef` 设置好。
 *
 * 1.21.1 改成了「`GuiGraphics` + `PoseStack` 显式传递」，而
 * `li.cil.oc.api.manual.ImageRenderer` 是**只读的 API 层接口**（Java），
 * 签名里不可能加 `GuiGraphics` 参数。因此这里用一个「当前帧的上下文」把
 * `GuiGraphics` 传进去：[[Document.render]] 在进入渲染时 [[push]]、
 * 结束时 [[pop]]，图片渲染器用 [[graphics]] 取回。
 *
 * 这是**单线程、严格嵌套**的使用场景（手册界面在一帧内的渲染是可重入的），
 * 所以用可变全局量是安全的。若将来有其它并发渲染路径，
 * 请改用显式的 `GuiGraphics` 参数并同步修改 API 层。
 */
object RenderContext {
  private var current: GuiGraphics = null

  /** 当前正在渲染的 `GuiGraphics`；不在手册渲染期间时为 `null`。 */
  def graphics: GuiGraphics = current

  /** 进入一层手册渲染；返回进入前的上下文，供 [pop] 恢复。 */
  def push(guiGraphics: GuiGraphics): GuiGraphics = {
    val previous = current
    current = guiGraphics
    previous
  }

  /** 退出当前层手册渲染，恢复为 [previous]。 */
  def pop(previous: GuiGraphics): Unit = {
    current = previous
  }

  /**
   * 在「当前 `GuiGraphics` 存在」时执行 `body`，否则跳过。
   *
   * 图片渲染器在异常路径上（例如界面正在被关闭）可能取不到上下文，
   * 这时直接跳过绘制而不是抛异常。
   */
  def withGraphics(body: GuiGraphics => Unit): Unit = {
    val guiGraphics = current
    if (guiGraphics != null) body(guiGraphics)
  }
}
