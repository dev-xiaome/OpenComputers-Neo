package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.Case
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation

object CaseRenderer extends BlockEntityRendererProvider[Case] {
  override def create(ctx: BlockEntityRendererProvider.Context): CaseRenderer =
    new CaseRenderer()
}

class CaseRenderer extends BlockEntityRenderer[Case] {

  override def render(computer: Case, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    stack.pushPose()

    stack.translate(0.5, 0.5, 0.5)

    computer.yaw match {
      case Direction.WEST => stack.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST => stack.mulPose(Axis.YP.rotationDegrees(90))
      case _ => // No yaw.
    }

    stack.translate(-0.5, 0.5, 0.505)
    RenderState.mirrorScale(stack, 1, -1, 1)

    val overlayBuffer = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)

    if (computer.isRunning) {
      renderFrontOverlay(stack, Textures.Block.CaseFrontOn, overlayBuffer)

      if (System.currentTimeMillis() - computer.lastFileSystemAccess < 400 && computer.getLevel.random.nextDouble() > 0.1) {
        renderFrontOverlay(stack, Textures.Block.CaseFrontActivity, overlayBuffer)
      }
    }
    else if (computer.hasErrored && RenderUtil.shouldShowErrorLight(computer.hashCode)) {
      renderFrontOverlay(stack, Textures.Block.CaseFrontError, overlayBuffer)
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