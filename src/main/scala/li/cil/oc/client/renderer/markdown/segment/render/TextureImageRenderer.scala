package li.cil.oc.client.renderer.markdown.segment.render

import java.io.InputStream

import com.mojang.blaze3d.platform.NativeImage
import li.cil.oc.OpenComputers
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.client.renderer.markdown.RenderContext
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

/**
 * 把一张普通贴图整张画进手册页面的图片渲染器。
 *
 * ==1.7.10 → 1.21.1==
 * 1.7.10 里自定义了一个 `AbstractTexture` 子类，手工 `ImageIO.read` +
 * `TextureUtil.uploadTextureImageAllocate` 上传纹理，绘制时再手动 `glBegin(GL_QUADS)`。
 *
 * 1.21.1 里：
 *  - 贴图由 `TextureManager` 按需加载（资源包重载后自动失效重载），
 *    因此**不再需要自行上传纹理**，只要知道贴图尺寸即可；
 *  - 尺寸通过 `NativeImage.read(resource.open())` 读一次并缓存；
 *  - 绘制改用 `GuiGraphics#blit` 的「指定纹理尺寸」重载。
 */
class TextureImageRenderer(val location: ResourceLocation) extends ImageRenderer {
  private val (textureWidth, textureHeight) = TextureImageRenderer.measure(location)

  override def getWidth: Int = textureWidth

  override def getHeight: Int = textureHeight

  override def render(mouseX: Int, mouseY: Int): Unit = {
    if (textureWidth <= 0 || textureHeight <= 0) return
    RenderContext.withGraphics { guiGraphics =>
      guiGraphics.blit(location, 0, 0, textureWidth, textureHeight,
        0f, 0f, textureWidth, textureHeight, textureWidth, textureHeight)
    }
  }
}

object TextureImageRenderer {
  /** 读取贴图尺寸；读不到时返回 `(0, 0)`（调用方会跳过绘制）。 */
  private def measure(location: ResourceLocation): (Int, Int) = {
    if (location == null) return (0, 0)
    var image: NativeImage = null
    var stream: InputStream = null
    try {
      // 1.21.1 的 `ResourceManager#getResource` 返回 `Optional[Resource]`（找不到资源不再抛异常）。
      val resource = Minecraft.getInstance().getResourceManager.getResource(location)
      if (!resource.isPresent) return (0, 0)
      stream = resource.get().open()
      image = NativeImage.read(stream)
      (image.getWidth, image.getHeight)
    } catch {
      case t: Throwable =>
        OpenComputers.log.debug(s"手册图片贴图读取失败：$location", t)
        (0, 0)
    } finally {
      if (image != null) image.close()
      if (stream != null) {
        try stream.close() catch {
          case _: Throwable =>
        }
      }
    }
  }
}
