package li.cil.oc.client.gui.widget

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex._
import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer

class ProgressBar(val x: Int, val y: Int) extends Widget {
  override def width = 140
  override def height = 12

  def barTexture = Textures.GUI.Bar
  var level = 0.0

  def draw(graphics: GuiGraphics): Unit = {
    if (level > 0) {
      val u0 = 0f
      val u1 = level.toFloat
      val v0 = 0f
      val v1 = 1f
      val tx = owner.windowX + x
      val ty = owner.windowY + y
      val w = (width * level).toFloat

      RenderSystem.setShader(() => GameRenderer.getPositionTexShader)
      RenderSystem.setShaderTexture(0, barTexture)
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

      val t = Tesselator.getInstance
      val r = t.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)

      r.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)

      val matrix = graphics.pose.last.pose
      r.addVertex(matrix, tx, ty, owner.windowZ).setUv(u0, v0)
      r.addVertex(matrix, tx, ty + height, owner.windowZ).setUv(u0, v1)
      r.addVertex(matrix, tx + w, ty + height, owner.windowZ).setUv(u1, v1)
      r.addVertex(matrix, tx + w, ty, owner.windowZ).setUv(u1, v0)

      com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(r.buildOrThrow())
    }
  }
}