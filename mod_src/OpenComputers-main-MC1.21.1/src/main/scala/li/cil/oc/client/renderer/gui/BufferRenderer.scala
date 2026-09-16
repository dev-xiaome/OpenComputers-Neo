package li.cil.oc.client.renderer.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.{BufferUploader, DefaultVertexFormat, PoseStack, Tesselator, VertexConsumer, VertexFormat}
import org.joml.Matrix4f
import li.cil.oc.api
import li.cil.oc.client.Textures
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GameRenderer

object BufferRenderer {
  val margin      = 7
  val innerMargin = 1

  def drawBackground(stack: PoseStack, bufferWidth: Int, bufferHeight: Int, forRobot: Boolean = false): Unit = {
    RenderState.checkError(getClass.getName + ".drawBackground: entering (aka: wasntme)")

    val innerWidth  = innerMargin * 2 + bufferWidth
    val innerHeight = innerMargin * 2 + bufferHeight

    RenderSystem.setShader(() => GameRenderer.getPositionTexShader)
    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    Textures.bind(Textures.GUI.Borders)

    val t = Tesselator.getInstance
    val r = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)

    val margin = if (forRobot) 2f else 7f
    val innerWidthF = innerWidth.toFloat
    val innerHeightF = innerHeight.toFloat
    val (c0, c1, c2, c3) = if (forRobot) (5f, 7f, 9f, 11f) else (0f, 7f, 9f, 16f)

    // Top border
    drawQuad(stack.last.pose(), r, 0f,                    0f,      margin,     margin,      c0,          c0, c1,          c1)
    drawQuad(stack.last.pose(), r, margin,               0f,      innerWidthF, margin,      c1 + 0.25f,  c0, c2 - 0.25f, c1)
    drawQuad(stack.last.pose(), r, margin + innerWidthF,  0f,      margin,     margin,      c2,          c0, c3,          c1)

    // Middle area
    drawQuad(stack.last.pose(), r, 0f,                    margin, margin,     innerHeightF, c0,          c1 + 0.25f, c1,          c2 - 0.25f)
    drawQuad(stack.last.pose(), r, margin,               margin, innerWidthF, innerHeightF, c1 + 0.25f,  c1 + 0.25f, c2 - 0.25f,  c2 - 0.25f)
    drawQuad(stack.last.pose(), r, margin + innerWidthF,  margin, margin,     innerHeightF, c2,          c1 + 0.25f, c3,          c2 - 0.25f)

    // Bottom border
    drawQuad(stack.last.pose(), r, 0f,                    margin + innerHeightF, margin,     margin, c0,          c2, c1,          c3)
    drawQuad(stack.last.pose(), r, margin,               margin + innerHeightF, innerWidthF, margin, c1 + 0.25f,  c2, c2 - 0.25f,  c3)
    drawQuad(stack.last.pose(), r, margin + innerWidthF,  margin + innerHeightF, margin,     margin, c2,          c2, c3,          c3)

    BufferUploader.drawWithShader(r.buildOrThrow())

    RenderState.checkError(getClass.getName + ".drawBackground: leaving")
  }

  private def drawQuad(
                        matrix: Matrix4f,
                        builder: VertexConsumer,
                        x: Float, y: Float, w: Float, h: Float,
                        u1: Float, v1: Float, u2: Float, v2: Float
                      ): Unit = {
    val u1f = u1 / 16f
    val u2f = u2 / 16f
    val v1f = v1 / 16f
    val v2f = v2 / 16f

    builder.addVertex(matrix, x,     y + h, 0f).setUv(u1f, v2f)
    builder.addVertex(matrix, x + w, y + h, 0f).setUv(u2f, v2f)
    builder.addVertex(matrix, x + w, y,     0f).setUv(u2f, v1f)
    builder.addVertex(matrix, x,     y,     0f).setUv(u1f, v1f)
  }

  def drawText(stack: PoseStack, screen: api.internal.TextBuffer): Unit = screen.renderText(stack)
}
