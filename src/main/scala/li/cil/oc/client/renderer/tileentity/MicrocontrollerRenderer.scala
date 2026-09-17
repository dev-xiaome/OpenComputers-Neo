package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.Microcontroller
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation

class MicrocontrollerRenderer(ctx: BlockEntityRendererProvider.Context) extends BlockEntityRenderer[Microcontroller] {

  override def render(mcu: Microcontroller, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

    stack.pushPose()

    stack.translate(0.5, 0.5, 0.5)

    mcu.yaw match {
      case Direction.WEST => stack.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST => stack.mulPose(Axis.YP.rotationDegrees(90))
      case _ => // No yaw.
    }

    stack.translate(-0.5, 0.5, 0.505)
    RenderState.mirrorScale(stack, 1.0F, -1.0F, 1.0F)

    val r = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)

    renderFrontOverlay(stack, Textures.Block.MicrocontrollerFrontLight, r)

    if (mcu.isRunning) {
      renderFrontOverlay(stack, Textures.Block.MicrocontrollerFrontOn, r)
    } else if (mcu.hasErrored && RenderUtil.shouldShowErrorLight(mcu.hashCode)) {
      renderFrontOverlay(stack, Textures.Block.MicrocontrollerFrontError, r)
    }

    stack.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }

  private def renderFrontOverlay(stack: PoseStack, texture: ResourceLocation, r: VertexConsumer): Unit = {
    val icon = Textures.getSprite(texture)
    val matrix = stack.last.pose

    r.addVertex(matrix, 0, 1, 0).setUv(icon.getU0, icon.getV1)
    r.addVertex(matrix, 1, 1, 0).setUv(icon.getU1, icon.getV1)
    r.addVertex(matrix, 1, 0, 0).setUv(icon.getU1, icon.getV0)
    r.addVertex(matrix, 0, 0, 0).setUv(icon.getU0, icon.getV0)
  }
}