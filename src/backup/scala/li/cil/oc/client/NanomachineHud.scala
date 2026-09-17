package li.cil.oc.client

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Controller
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * 纳米机器的 HUD 电量条（从 `common/event/NanomachinesHandler.Client` 整体搬过来的）。
 *
 * ==为什么在 `client` 包里==
 * 这段代码只用得上客户端类（`Minecraft` / `GuiGraphics` / 客户端贴图 [[Textures]]）。
 * 1.7.10 把它和控制器生命周期放在同一个 `common.event.NanomachinesHandler` 里，
 * 用一个 `object Client` 子对象隔离；1.21.1 要求 `common` 包**不能有**对 `client` 包的
 * 编译期引用（否则整个 `client` 会被拖进编译集，见 [[li.cil.oc.common.ClientHooks]]），
 * 所以这里把它整体搬到 `client` 包，由 [[ClientListeners]] 注册。
 *
 * 控制器生命周期（创建 / 重建 / 卸载、状态持久化）仍然留在
 * [[li.cil.oc.common.event.NanomachinesHandler.Common]]，不依赖客户端。
 *
 * 1.21.1 迁移要点：
 *  - `RenderGameOverlayEvent.Post`（带 `ElementType.TEXT` 判定）→ `RenderGuiEvent.Post` +
 *    `GuiGraphics`：`ScaledResolution` → `GuiGraphics#guiWidth/guiHeight`，
 *    `Tessellator` + `bindTexture` → `GuiGraphics#blit`。
 */
object NanomachineHud {
  /** 注册监听器；由 [[ClientListeners]] 调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: RenderGuiEvent.Post) => onRenderGameOverlay(e))
  }

  def onRenderGameOverlay(e: RenderGuiEvent.Post): Unit = {
    val mc = Minecraft.getInstance
    val player = mc.player
    if (player == null) return
    api.Nanomachines.getController(player) match {
      case controller: Controller =>
        val graphics = e.getGuiGraphics
        val sizeX = 8
        val sizeY = 12
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val (x, y) = Settings.get.nanomachineHudPos
        val left =
          math.min(width - sizeX,
            if (x < 0) width / 2 - 91 - 12
            else if (x < 1) width * x
            else x)
        val top =
          math.min(height - sizeY,
            if (y < 0) height - 39
            else if (y < 1) y * height
            else y)
        val size = controller.getLocalBufferSize
        val fill = if (size <= 0) 0.0 else controller.getLocalBuffer / size

        graphics.blit(Textures.overlayNanomachines, left.toInt, top.toInt, 0f, 0f, sizeX, sizeY, sizeX, sizeY)

        // 电量条自下往上填充（与 1.7.10 版本一致）。
        val barHeight = math.max(0, math.min(sizeY, (sizeY * fill).toInt))
        if (barHeight > 0) {
          graphics.blit(Textures.overlayNanomachinesBar,
            left.toInt, top.toInt + sizeY - barHeight,
            0f, (sizeY - barHeight).toFloat,
            sizeX, barHeight, sizeX, sizeY)
        }
      case _ => // 没有可显示的内容。
    }
  }
}
