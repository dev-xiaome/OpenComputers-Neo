package li.cil.oc.client.renderer.font

import com.google.common.base.Charsets
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix4f
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import scala.io.Source

class StaticFontRenderer extends TextureFontRenderer {
  protected val (chars, charWidth, charHeight) = try {
    val manager = Minecraft.getInstance.getResourceManager
    val location = ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/font/chars.txt")
    val optRes = manager.getResource(location)
    if (optRes.isPresent) {
      val is = optRes.get.open
      val lines = Source.fromInputStream(is)(Charsets.UTF_8).getLines()
      val charStr = lines.next()
      val (w, h) = if (lines.hasNext) {
        val size = lines.next().split(" ", 2)
        (size(0).toInt, size(1).toInt)
      } else (10, 18)
      (charStr, w, h)
    } else {
      (basicChars, 10, 18)
    }
  } catch {
    case t: Throwable =>
      OpenComputers.log.warn("Failed reading font metadata, using defaults.", t)
      (basicChars, 10, 18)
  }

  private val cols = 256 / charWidth
  private val uStep = charWidth / 256f
  private val vStep = (charHeight + 1) / 256f
  private val vSize = charHeight / 256f
  private val s = Settings.get.fontCharScale.toFloat
  private val dw = charWidth * s - charWidth
  private val dh = charHeight * s - charHeight

  override protected def textureCount = 1

  override protected def selectType(index: Int): RenderType = {
    val isAntiAlias = Settings.get.textAntiAlias
    val location = if (isAntiAlias) Textures.Font.AntiAliased else Textures.Font.Aliased
    RenderTypes.createFontTex(location.getPath, location, isAntiAlias)
  }

  override protected def generateChar(char: Int): Unit = {}

  override protected def drawChar(builder: VertexConsumer, matrix: Matrix4f, color: Int, tx: Float, ty: Float, char: Int): Unit = {
    val index = chars.indexOf(char) match {
      case -1 => chars.indexOf('?')
      case i => i
    }
    val x = index % cols
    val y = index / cols
    val u = x * uStep
    val v = y * vStep
    val r = ((color >> 16) & 0xFF) / 255f
    val g = ((color >> 8) & 0xFF) / 255f
    val b = (color & 0xFF) / 255f

    builder.addVertex(matrix, tx - dw, ty + charHeight * s, 0).setColor(r, g, b, 1f).setUv(u, v + vSize)
    builder.addVertex(matrix, tx + charWidth * s, ty + charHeight * s, 0).setColor(r, g, b, 1f).setUv(u + uStep, v + vSize)
    builder.addVertex(matrix, tx + charWidth * s, ty - dh, 0).setColor(r, g, b, 1f).setUv(u + uStep, v)
    builder.addVertex(matrix, tx - dw, ty - dh, 0).setColor(r, g, b, 1f).setUv(u, v)
  }
}