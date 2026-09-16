package li.cil.oc.client.renderer.entity

import com.mojang.blaze3d.vertex.PoseStack  // 1.18.2: com.mojang.blaze3d.matrix.MatrixStack → PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.entity.Drone
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider  // 1.18.2: EntityRenderDispatcher → EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth

class DroneRenderer(ctx: EntityRendererProvider.Context) extends EntityRenderer[Drone](ctx) {
  private val model = new ModelQuadcopter(ctx.bakeLayer(ModelQuadcopter.LAYER_LOCATION))

  override def render(entity: Drone, yaw: Float, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int): Unit = {
    val renderType = getRenderType(entity)
    if (renderType != null) {
      stack.pushPose()
      stack.translate(0, 2f / 16f, 0)
      val builder = buffer.getBuffer(renderType)
      model.prepareMobModel(entity, 0, 0, dt)
      val xRot = Mth.rotLerp(dt, entity.xRotO, entity.getXRot)
      val yRot = Mth.rotLerp(dt, entity.yRotO, entity.getYRot)
      model.setupAnim(entity, 0, 0, entity.tickCount.toFloat, yRot, xRot)
      model.renderToBuffer(stack, builder, light, OverlayTexture.NO_OVERLAY)
      stack.popPose()
    }
    super.render(entity, yaw, dt, stack, buffer, light)
  }

  override def getTextureLocation(entity: Drone) = Textures.Model.Drone

  def getRenderType(entity: Drone): RenderType = {
    val mc      = Minecraft.getInstance
    val texture = getTextureLocation(entity)
    if (!entity.isInvisible)                             model.renderType(texture)
    else if (!entity.isInvisibleTo(mc.player))           RenderType.itemEntityTranslucentCull(texture)
    else if (mc.shouldEntityAppearGlowing(entity))       RenderType.outline(texture)
    else null
  }
}