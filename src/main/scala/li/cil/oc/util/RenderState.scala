package li.cil.oc.util

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import org.lwjgl.opengl.{GL11, GL30}

/**
 * 渲染状态 / OpenGL 工具。
 *
 * 1.21.1 迁移要点：
 *  - `Minecraft.entityRenderer.enableLightmap/disableLightmap` →
 *    `GameRenderer#lightTexture().turnOnLightLayer()/turnOffLightLayer()`
 *  - `RenderHelper.enableStandardItemLighting/disableStandardItemLighting` 已被移除，
 *    光照由 `LightTexture` + 着色器管线统一管理
 *  - `GLContext.getCapabilities` 已移除（LWJGL 3 无该 API），多纹理是必备能力
 *  - 固定管线的显示列表已从渲染管线移除，`compilingDisplayList` 恒为 `false`
 *  - `GL11.glColor4f` / `glBlendFunc` 等固定管线调用改为 `RenderSystem`
 *  - `org.lwjgl.util.glu.GLU` 在 LWJGL 3 中已整体移除（`gluErrorString` 随之消失），
 *    这里用本地的错误码名字表替代，仅用于日志输出
 *
 * TODO(渲染): 本文件保留方法签名以兼容既有调用方；后续渲染层重写为
 * `PoseStack` + `VertexConsumer` 后，这些 OpenGL 包装应整体删除。
 */
object RenderState {
  /** 1.21.1 要求 OpenGL 1.3+（含多纹理），恒为 `false`（不再需要回退路径）。 */
  val arb: Boolean = false

  def checkError(where: String): Unit = {
    if (Settings.get.logOpenGLErrors) {
      val error = GL11.glGetError
      if (error != 0) {
        OpenComputers.log.warn("GL ERROR @ " + where + ": " + glErrorString(error))
      }
    }
  }

  /**
   * TODO(渲染): LWJGL 3 移除了 `GLU.gluErrorString`，这里按 OpenGL 规范给出错误码名称；
   * 表里没有的编码回退为十六进制，保证日志始终可读。
   */
  private def glErrorString(error: Int): String = error match {
    case GL11.GL_INVALID_ENUM => "GL_INVALID_ENUM"
    case GL11.GL_INVALID_VALUE => "GL_INVALID_VALUE"
    case GL11.GL_INVALID_OPERATION => "GL_INVALID_OPERATION"
    case GL11.GL_STACK_OVERFLOW => "GL_STACK_OVERFLOW"
    case GL11.GL_STACK_UNDERFLOW => "GL_STACK_UNDERFLOW"
    case GL11.GL_OUT_OF_MEMORY => "GL_OUT_OF_MEMORY"
    case GL30.GL_INVALID_FRAMEBUFFER_OPERATION => "GL_INVALID_FRAMEBUFFER_OPERATION"
    case other => "GL error 0x" + Integer.toHexString(other)
  }

  /**
   * TODO(渲染): 1.21.1 已移除固定管线的显示列表，`GL_LIST_INDEX` 查询不再有意义。
   */
  def compilingDisplayList: Boolean = false

  def disableLighting(): Unit = {
    // 关闭光照层（原为 entityRenderer.disableLightmap + RenderHelper.disableStandardItemLighting）。
    lightTexture.turnOffLightLayer()
  }

  def enableLighting(): Unit = {
    lightTexture.turnOnLightLayer()
  }

  private def lightTexture: LightTexture = Minecraft.getInstance.gameRenderer.lightTexture()

  /** 开启常规 Alpha 混合。 */
  def makeItBlend(): Unit = {
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()
  }

  /**
   * TODO(渲染): 1.21.1 无 `glColor4f`，透明度改为着色器颜色 /
   * `VertexConsumer#color(float, float, float, float)`。
   */
  def setBlendAlpha(alpha: Float): Unit = {
    RenderSystem.enableBlend()
    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
    RenderSystem.setShaderColor(1f, 1f, 1f, alpha)
  }
}
