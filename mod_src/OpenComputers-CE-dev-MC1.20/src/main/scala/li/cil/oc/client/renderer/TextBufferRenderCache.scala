package li.cil.oc.client.renderer

import java.util.concurrent.TimeUnit
import com.google.common.cache.CacheBuilder
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.{DefaultVertexFormat, PoseStack, Tesselator, VertexFormat}
import li.cil.oc.{OpenComputers, Settings}
import li.cil.oc.client.renderer.font.TextBufferRenderData
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.{GameRenderer, MultiBufferSource}
import net.minecraftforge.event.TickEvent.ClientTickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object TextBufferRenderCache {
  val renderer =
    if (Settings.get.fontRenderer == "texture") new font.StaticFontRenderer()
    else new font.DynamicFontRenderer()

  private val cache = com.google.common.cache.CacheBuilder.newBuilder().
    expireAfterAccess(2, TimeUnit.SECONDS).
    build[TextBufferRenderData, RenderCache]()

  // ----------------------------------------------------------------------- //
  // Rendering
  // ----------------------------------------------------------------------- //

  def render(stack: PoseStack, buffer: TextBufferRenderData): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    val cached = cache.get(buffer, () => new RenderCache)
    if (buffer.dirty || cached.isEmpty) {
      for (line <- buffer.data.buffer) {
        renderer.generateChars(line)
      }

      buffer.dirty = false
      cached.clear()

      renderer.drawBuffer(new PoseStack(), cached, buffer.data, buffer.viewport._1, buffer.viewport._2)
      cached.finish()
    }

    cached.render(stack)

    RenderState.checkError(getClass.getName + ".render: leaving")
  }

  def renderImmediate(stack: PoseStack, renderBuffer: MultiBufferSource, buffer: TextBufferRenderData): Unit = {
    RenderState.checkError(getClass.getName + ".renderImmediate: entering")

    for (line <- buffer.data.buffer) {
      renderer.generateChars(line)
    }

    renderer.drawBuffer(stack, renderBuffer, buffer.data, buffer.viewport._1, buffer.viewport._2)

    RenderState.checkError(getClass.getName + ".renderImmediate: leaving")
  }

  // ----------------------------------------------------------------------- //
  // ITickHandler
  // ----------------------------------------------------------------------- //

  @SubscribeEvent
  def onTick(e: ClientTickEvent) = cache.cleanUp()
}
