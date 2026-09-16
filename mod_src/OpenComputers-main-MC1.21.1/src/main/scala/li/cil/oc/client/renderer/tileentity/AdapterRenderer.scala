package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer, BlockEntityRendererProvider}
import net.minecraft.core.Direction

object AdapterRenderer extends BlockEntityRendererProvider[blockentity.Adapter] {
  override def create(ctx: BlockEntityRendererProvider.Context): AdapterRenderer =
    new AdapterRenderer()
}

class AdapterRenderer extends BlockEntityRenderer[blockentity.Adapter] {

  override def render(adapter: blockentity.Adapter, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1, 1, 1, 1)

    if (adapter.openSides.contains(true)) {
      stack.pushPose()

      stack.translate(0.5, 0.5, 0.5)
      RenderState.mirrorScale(stack, 1.0025f, -1.0025f, 1.0025f)
      stack.translate(-0.5f, -0.5f, -0.5f)

      val vBuffer = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
      val sideActivity = Textures.getSprite(Textures.Block.AdapterOn)
      val matrix = stack.last.pose

      if (adapter.isSideOpen(Direction.DOWN)) {
        vBuffer.addVertex(matrix, 0, 1, 0).setUv(sideActivity.getU1, sideActivity.getV0)
        vBuffer.addVertex(matrix, 1, 1, 0).setUv(sideActivity.getU0, sideActivity.getV0)
        vBuffer.addVertex(matrix, 1, 1, 1).setUv(sideActivity.getU0, sideActivity.getV1)
        vBuffer.addVertex(matrix, 0, 1, 1).setUv(sideActivity.getU1, sideActivity.getV1)
      }

      if (adapter.isSideOpen(Direction.UP)) {
        vBuffer.addVertex(matrix, 0, 0, 0).setUv(sideActivity.getU1, sideActivity.getV1)
        vBuffer.addVertex(matrix, 0, 0, 1).setUv(sideActivity.getU1, sideActivity.getV0)
        vBuffer.addVertex(matrix, 1, 0, 1).setUv(sideActivity.getU0, sideActivity.getV0)
        vBuffer.addVertex(matrix, 1, 0, 0).setUv(sideActivity.getU0, sideActivity.getV1)
      }

      if (adapter.isSideOpen(Direction.NORTH)) {
        vBuffer.addVertex(matrix, 1, 1, 0).setUv(sideActivity.getU0, sideActivity.getV1)
        vBuffer.addVertex(matrix, 0, 1, 0).setUv(sideActivity.getU1, sideActivity.getV1)
        vBuffer.addVertex(matrix, 0, 0, 0).setUv(sideActivity.getU1, sideActivity.getV0)
        vBuffer.addVertex(matrix, 1, 0, 0).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      if (adapter.isSideOpen(Direction.SOUTH)) {
        vBuffer.addVertex(matrix, 0, 1, 1).setUv(sideActivity.getU0, sideActivity.getV1)
        vBuffer.addVertex(matrix, 1, 1, 1).setUv(sideActivity.getU1, sideActivity.getV1)
        vBuffer.addVertex(matrix, 1, 0, 1).setUv(sideActivity.getU1, sideActivity.getV0)
        vBuffer.addVertex(matrix, 0, 0, 1).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      if (adapter.isSideOpen(Direction.WEST)) {
        vBuffer.addVertex(matrix, 0, 1, 0).setUv(sideActivity.getU0, sideActivity.getV1)
        vBuffer.addVertex(matrix, 0, 1, 1).setUv(sideActivity.getU1, sideActivity.getV1)
        vBuffer.addVertex(matrix, 0, 0, 1).setUv(sideActivity.getU1, sideActivity.getV0)
        vBuffer.addVertex(matrix, 0, 0, 0).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      if (adapter.isSideOpen(Direction.EAST)) {
        vBuffer.addVertex(matrix, 1, 1, 1).setUv(sideActivity.getU0, sideActivity.getV1)
        vBuffer.addVertex(matrix, 1, 1, 0).setUv(sideActivity.getU1, sideActivity.getV1)
        vBuffer.addVertex(matrix, 1, 0, 0).setUv(sideActivity.getU1, sideActivity.getV0)
        vBuffer.addVertex(matrix, 1, 0, 1).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      stack.popPose()
    }

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}