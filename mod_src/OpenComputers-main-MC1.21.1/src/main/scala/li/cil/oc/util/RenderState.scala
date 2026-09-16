package li.cil.oc.util

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.util.Mth
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import org.joml.{Matrix3f, Matrix4f}
import org.lwjgl.opengl._

// This class has evolved into a wrapper for RenderSystem that basically does
// nothing but call the corresponding RenderSystem methods and then also
// forcefully applies whatever that call *should* do. This way the state
// manager's internal state is kept up-to-date but we also avoid issues with
// that state being incorrect causing wrong behavior (I've had too many render
// bugs where textures were not bound correctly or state was not updated
// because the state manager thought it already was in the state to change to,
// so I frankly don't care if this is less performant anymore).
@OnlyIn(Dist.CLIENT)
object RenderState {
  def getErrorString(errorCode: Int): String = errorCode match {
    case GL11.GL_NO_ERROR => "No error"
    case GL11.GL_INVALID_ENUM => "Enum argument out of range"
    case GL11.GL_INVALID_VALUE => "Numeric argument out of range"
    case GL11.GL_INVALID_OPERATION => "Operation illegal in current state"
    case GL11.GL_STACK_OVERFLOW => "Command would cause a stack overflow"
    case GL11.GL_STACK_UNDERFLOW => "Command would cause a stack underflow"
    case GL11.GL_OUT_OF_MEMORY => "Not enough memory left to execute command"
    case _ => f"Unknown [0x$errorCode%X]"
  }

  def checkError(where: String): Unit = {
    if (Settings.get.logOpenGLErrors) {
      val error = GL11.glGetError
      if (error != 0) {
        OpenComputers.log.warn("GL ERROR @ " + where + ": " + getErrorString(error))
      }
    }
  }


  def makeItBlend(): Unit = {
    RenderSystem.enableBlend()
    GL11.glEnable(GL11.GL_BLEND)
    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
  }

  def disableBlend(): Unit = {
    RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ZERO)
    RenderSystem.disableBlend()
    GL11.glDisable(GL11.GL_BLEND)
  }

  def setBlendAlpha(alpha: Float) = {
    RenderSystem.setShaderColor(1, 1, 1, alpha)
    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
  }

  def bindTexture(id: Int): Unit = {
    RenderSystem.bindTexture(id)
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
  }

  def mirrorScale(stack: PoseStack, sx: Float, sy: Float, sz: Float): Unit = {
    stack.last.pose.mul(new Matrix4f().scaling(sx, sy, sz))
    if (sx != sy || sx != sz || sx <= 0) {
      val isx = 1 / sx
      val isy = 1 / sy
      val isz = 1 / sz
      val invScale = isx * isy * isz
      // Issue with vanilla impl: the inverse cube root algorithm completely fails for negative values.
      var normScale = Mth.fastInvCubeRoot(Mth.abs(invScale))
      if (invScale < 0) {
        // compensate for taking the absolute of invScale
        normScale = -normScale
      }
      stack.last.normal.mul(new Matrix3f().scaling(isx * normScale, isy * normScale, isz * normScale))
    }
  }
}
