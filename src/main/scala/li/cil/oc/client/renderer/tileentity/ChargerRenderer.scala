package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.Charger
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction

object ChargerRenderer extends BlockEntityRendererProvider[Charger] {
  override def create(ctx: BlockEntityRendererProvider.Context): ChargerRenderer =
    new ChargerRenderer()
}

class ChargerRenderer extends BlockEntityRenderer[Charger] {

  override def render(charger: Charger, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1, 1, 1, 1)

    if (charger.chargeSpeed > 0) {
      stack.pushPose()

      stack.translate(0.5, 0.5, 0.5)

      charger.yaw match {
        case Direction.WEST => stack.mulPose(Axis.YP.rotationDegrees(-90))
        case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
        case Direction.EAST => stack.mulPose(Axis.YP.rotationDegrees(90))
        case _ => // No yaw.
      }

      stack.translate(-0.5f, 0.5f, 0.5f)
      RenderState.mirrorScale(stack, 1, -1, 1)

      val vBuffer = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
      val matrix = stack.last.pose

      {
        val inverse = 1 - charger.chargeSpeed.toFloat
        val icon = Textures.getSprite(Textures.Block.ChargerFrontOn)
        vBuffer.addVertex(matrix, 0, 1, 0.005f).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 1, 1, 0.005f).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 1, inverse, 0.005f).setUv(icon.getU1, icon.getV(inverse))
        vBuffer.addVertex(matrix, 0, inverse, 0.005f).setUv(icon.getU0, icon.getV(inverse))
      }

      if (charger.hasPower) {
        val icon = Textures.getSprite(Textures.Block.ChargerSideOn)

        vBuffer.addVertex(matrix, -0.005f, 1, -1).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, -0.005f, 1, 0).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, -0.005f, 0, 0).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, -0.005f, 0, -1).setUv(icon.getU0, icon.getV0)

        vBuffer.addVertex(matrix, 1, 1, -1.005f).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 0, 1, -1.005f).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 0, 0, -1.005f).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 1, 0, -1.005f).setUv(icon.getU0, icon.getV0)

        vBuffer.addVertex(matrix, 1.005f, 1, 0).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 1.005f, 1, -1).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 1.005f, 0, -1).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 1.005f, 0, 0).setUv(icon.getU0, icon.getV0)
      }

      stack.popPose()
    }

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}
