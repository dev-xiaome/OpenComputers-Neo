package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.Assembler
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

object AssemblerRenderer extends BlockEntityRendererProvider[Assembler] {
  override def create(ctx: BlockEntityRendererProvider.Context): AssemblerRenderer =
    new AssemblerRenderer()
}

class AssemblerRenderer extends BlockEntityRenderer[Assembler] {

  override def render(assembler: Assembler, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1, 1, 1, 1)

    stack.pushPose()

    stack.translate(0.5, 0.5, 0.5)

    val vBuffer = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
    val matrix = stack.last.pose

    {
      val icon = Textures.getSprite(Textures.Block.AssemblerTopOn)
      vBuffer.addVertex(matrix, -0.5f, 0.55f, 0.5f).setUv(icon.getU0, icon.getV1)
      vBuffer.addVertex(matrix, 0.5f, 0.55f, 0.5f).setUv(icon.getU1, icon.getV1)
      vBuffer.addVertex(matrix, 0.5f, 0.55f, -0.5f).setUv(icon.getU1, icon.getV0)
      vBuffer.addVertex(matrix, -0.5f, 0.55f, -0.5f).setUv(icon.getU0, icon.getV0)
    }

    val indent = 6 / 16f + 0.005f
    for (_ <- 0 until 4) {
      if (assembler.isAssembling) {
        val icon = Textures.getSprite(Textures.Block.AssemblerSideAssembling)
        vBuffer.addVertex(matrix, indent, 0.5f, -indent).setUv(icon.getU(0.5f - indent), icon.getV1)
        vBuffer.addVertex(matrix, indent, 0.5f, indent).setUv(icon.getU(0.5f + indent), icon.getV1)
        vBuffer.addVertex(matrix, indent, -0.5f, indent).setUv(icon.getU(0.5f + indent), icon.getV0)
        vBuffer.addVertex(matrix, indent, -0.5f, -indent).setUv(icon.getU(0.5f - indent), icon.getV0)
      }

      {
        val icon = Textures.getSprite(Textures.Block.AssemblerSideOn)
        vBuffer.addVertex(matrix, 0.5005f, 0.5f, -0.5f).setUv(icon.getU0, icon.getV1)
        vBuffer.addVertex(matrix, 0.5005f, 0.5f, 0.5f).setUv(icon.getU1, icon.getV1)
        vBuffer.addVertex(matrix, 0.5005f, -0.5f, 0.5f).setUv(icon.getU1, icon.getV0)
        vBuffer.addVertex(matrix, 0.5005f, -0.5f, -0.5f).setUv(icon.getU0, icon.getV0)
      }

      stack.mulPose(Axis.YP.rotationDegrees(90))
    }

    stack.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}
