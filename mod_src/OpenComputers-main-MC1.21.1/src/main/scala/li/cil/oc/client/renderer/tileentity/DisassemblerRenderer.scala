package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

object DisassemblerRenderer extends BlockEntityRendererProvider[blockentity.Disassembler] {
  override def create(ctx: BlockEntityRendererProvider.Context): DisassemblerRenderer =
    new DisassemblerRenderer()
}

class DisassemblerRenderer extends BlockEntityRenderer[blockentity.Disassembler] {

  override def render(disassembler: blockentity.Disassembler, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

    if (disassembler.isActive) {
      stack.pushPose()

      stack.translate(0.5, 0.5, 0.5)
      RenderState.mirrorScale(stack, 1.0025f, -1.0025f, 1.0025f)
      stack.translate(-0.5, -0.5, -0.5)

      val vBuffer = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
      val matrix = stack.last.pose

      {
        val icon = Textures.getSprite(Textures.Block.DisassemblerTopOn)
        vBuffer.addVertex(matrix, 0, 0, 1).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 1, 0, 1).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 1, 0, 0).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 0, 0, 0).setUv(icon.getU0, icon.getV0)
      }

      {
        val icon = Textures.getSprite(Textures.Block.DisassemblerSideOn)

        // North
        vBuffer.addVertex(matrix, 1, 1, 0).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 0, 1, 0).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 0, 0, 0).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 1, 0, 0).setUv(icon.getU0, icon.getV0)

        // South
        vBuffer.addVertex(matrix, 0, 1, 1).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 1, 1, 1).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 1, 0, 1).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 0, 0, 1).setUv(icon.getU0, icon.getV0)

        // East
        vBuffer.addVertex(matrix, 1, 1, 1).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 1, 1, 0).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 1, 0, 0).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 1, 0, 1).setUv(icon.getU0, icon.getV0)

        // West
        vBuffer.addVertex(matrix, 0, 1, 0).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 0, 1, 1).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 0, 0, 1).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 0, 0, 0).setUv(icon.getU0, icon.getV0)
      }

      stack.popPose()
    }

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}