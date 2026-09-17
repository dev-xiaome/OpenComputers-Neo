package li.cil.oc.client.gui.traits

import li.cil.oc.client.gui.widget.WidgetContainer
import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

/**
 * 在界面里显示一块文本缓冲区（屏幕 / 机器人 / 无人机界面共用）。
 *
 * 1.7.10 的 `trait DisplayBuffer extends GuiScreen` 负责：
 *  - 在 `initGui` 时初始化 [[BufferRenderer]]；
 *  - 每帧算出缩放比例 [[scale]]（由子类实现 [[changeSize]]）；
 *  - 包一层 `glPushMatrix` / `glPopMatrix` 后调用子类的绘制入口。
 *
 * ==1.21.1 迁移要点==
 *  - `initGui()` → `init()`；
 *  - `glPushMatrix` / `glPopMatrix` → [[com.mojang.blaze3d.vertex.PoseStack]]
 *    （从 [[GuiGraphics#pose]] 取），`RenderState.disableLighting()` 保留（它是光照层的开关）；
 *  - 绘制入口由无参的 `drawBuffer()` 改为 `drawBuffer(guiGraphics)`，
 *    调用方（各界面）在**自己的**前景/背景绘制里调用 [[drawBufferLayer]] 并传入
 *    `GuiGraphics`。
 *
 * ==与 1.7.10 的差异==
 *  - [[drawBufferLayer]] 需要 `GuiGraphics` 参数；
 *  - `BufferRenderer` / `TextBufferRenderCache` 仍由另一个子代理同时移植，
 *    这里按它们**当前公开的签名**调用（`BufferRenderer.init(TextureManager)`）。
 *
 * ==为什么要顺带混入 [[WidgetContainer]]==
 * 本 trait 继承 [[Screen]]，而屏幕界面（`Case` / `Robot` / `Drone` / …）还需要
 * [[WidgetContainer]] 提供的 `widgets` / `windowX` / `windowY` / [[WidgetContainer.drawWidgets]]，
 * 所以这里把它一起混进来。
 *
 * 注意一个已踩过的坑：`Screen` 在 1.21.1 里**也有**一个 `addWidget`（把原版控件加进屏幕，
 * `T <: GuiEventListener with NarratableEntry`）。它与 `WidgetContainer` 的小组件版本同名、
 * 泛型边界不同，但 Scala 2.13 会把它判定为「同一个成员的两个定义」，
 * 于是每个界面类都报 `inherits conflicting members`（2.11 放过了这种写法）。
 * 因此 [[WidgetContainer]] 的入口已改名为 [[WidgetContainer.addWidgetToContainer]]，
 * 两者不再相撞；`Screen#addWidget` 保持原样，继续由原版内部使用。
 */
trait DisplayBuffer extends Screen with WidgetContainer {
  protected def bufferX: Int

  protected def bufferY: Int

  protected def bufferColumns: Int

  protected def bufferRows: Int

  protected var guiSizeChanged = false

  protected var currentWidth, currentHeight = -1

  protected var scale = 0.0

  override protected def init(): Unit = {
    super.init()
    // 原 1.7.10 是 BufferRenderer.init(mc.renderEngine)。1.21.1 的 TextureManager 没有
    // bindTexture 预热，但 BufferRenderer 仍然想拿到它（内部按需取贴图），签名保持不变。
    BufferRenderer.init(Minecraft.getInstance.getTextureManager)
    guiSizeChanged = true
  }

  protected def drawBufferLayer(guiGraphics: GuiGraphics): Unit = {
    val oldWidth = currentWidth
    val oldHeight = currentHeight
    currentWidth = bufferColumns
    currentHeight = bufferRows
    scale = changeSize(currentWidth, currentHeight, guiSizeChanged || oldWidth != currentWidth || oldHeight != currentHeight)

    RenderState.checkError(getClass.getName + ".drawBufferLayer: entering (aka: wasntme)")

    val pose = guiGraphics.pose()
    pose.pushPose()
    RenderState.disableLighting()
    drawBuffer(guiGraphics)
    pose.popPose()

    RenderState.checkError(getClass.getName + ".drawBufferLayer: buffer layer")
  }

  /** 子类的缓冲区绘制入口；调用时 pose 已经 push 过，坐标系与 1.7.10 一致。 */
  protected def drawBuffer(guiGraphics: GuiGraphics): Unit

  /** 按缓冲区尺寸算出缩放比例，并在需要时通知子类重建缓存。 */
  protected def changeSize(w: Double, h: Double, recompile: Boolean): Double
}
