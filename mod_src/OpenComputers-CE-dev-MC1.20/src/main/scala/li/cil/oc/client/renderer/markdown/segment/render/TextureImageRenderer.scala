package li.cil.oc.client.renderer.markdown.segment.render

import java.io.{IOException, InputStream}
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex._
import li.cil.oc.api.manual.ImageRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.gui.GuiGraphics

class TextureImageRenderer(val location: ResourceLocation) extends ImageRenderer {
  private val texture: ImageTexture = {
    val manager = Minecraft.getInstance.getTextureManager
    manager.getTexture(location) match {
      case image: ImageTexture => image
      case _ =>
        val image = new ImageTexture(location)
        manager.register(location, image)
        image
    }
  }

  override def getWidth: Int = texture.width

  override def getHeight: Int = texture.height

  override def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    RenderSystem.setShaderTexture(0, location)
    RenderSystem.setShader(() => GameRenderer.getPositionTexShader)
    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)

    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()

    val matrix = graphics.pose.last.pose
    val tesselator = Tesselator.getInstance()
    val builder = tesselator.getBuilder

    builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
    builder.vertex(matrix, 0, texture.height.toFloat, 0).uv(0, 1).endVertex()
    builder.vertex(matrix, texture.width.toFloat, texture.height.toFloat, 0).uv(1, 1).endVertex()
    builder.vertex(matrix, texture.width.toFloat, 0, 0).uv(1, 0).endVertex()
    builder.vertex(matrix, 0, 0, 0).uv(0, 0).endVertex()
    tesselator.end()

    RenderSystem.disableBlend()
  }

  private class ImageTexture(val resLoc: ResourceLocation) extends AbstractTexture {
    var width = 0
    var height = 0

    override def load(manager: ResourceManager): Unit = {
      this.releaseId()

      val resOpt = manager.getResource(resLoc)
      if (resOpt.isEmpty) return

      var is: InputStream = null
      try {
        is = resOpt.get.open
        val nativeImage = NativeImage.read(is)
        try {
          this.width = nativeImage.getWidth
          this.height = nativeImage.getHeight

          RenderSystem.recordRenderCall(() => {
            TextureUtil.prepareImage(this.getId, this.width, this.height)
            nativeImage.upload(0, 0, 0, false)
          })
        } finally {
          RenderSystem.recordRenderCall(() => nativeImage.close())
        }
      } catch {
        case _: IOException =>
      } finally {
        if (is != null) is.close()
      }
    }
  }
}