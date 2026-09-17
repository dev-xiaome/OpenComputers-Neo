package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer => TileEntityRenderer}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

object PowerDistributorRenderer extends BlockEntityRendererProvider[blockentity.PowerDistributor] {
  override def create(ctx: BlockEntityRendererProvider.Context): PowerDistributorRenderer =
    new PowerDistributorRenderer()
}

class PowerDistributorRenderer extends TileEntityRenderer[blockentity.PowerDistributor] {
  override def render(
                       distributor: blockentity.PowerDistributor,
                       dt: Float,
                       stack: PoseStack,
                       buffer: MultiBufferSource,
                       light: Int,
                       overlay: Int
                     ): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering (aka: wasntme)")

    RenderSystem.setShaderColor(1, 1, 1, 1)

    if (distributor.globalBuffer > 0) {
      stack.pushPose()

      stack.translate(0.5, 0.5, 0.5)
      RenderState.mirrorScale(stack, 1.0025f, -1.0025f, 1.0025f)
      stack.translate(-0.5f, -0.5f, -0.5f)

      val r = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)

      {
        val icon = Textures.getSprite(Textures.Block.PowerDistributorTopOn)
        r.addVertex(stack.last.pose, 0, 0, 1).setUv(icon.getU0, icon.getV1)
        r.addVertex(stack.last.pose, 1, 0, 1).setUv(icon.getU1, icon.getV1)
        r.addVertex(stack.last.pose, 1, 0, 0).setUv(icon.getU1, icon.getV0)
        r.addVertex(stack.last.pose, 0, 0, 0).setUv(icon.getU0, icon.getV0)
      }

      {
        val icon = Textures.getSprite(Textures.Block.PowerDistributorSideOn)
        r.addVertex(stack.last.pose, 1, 1, 0).setUv(icon.getU0, icon.getV1)
        r.addVertex(stack.last.pose, 0, 1, 0).setUv(icon.getU1, icon.getV1)
        r.addVertex(stack.last.pose, 0, 0, 0).setUv(icon.getU1, icon.getV0)
        r.addVertex(stack.last.pose, 1, 0, 0).setUv(icon.getU0, icon.getV0)

        r.addVertex(stack.last.pose, 0, 1, 1).setUv(icon.getU0, icon.getV1)
        r.addVertex(stack.last.pose, 1, 1, 1).setUv(icon.getU1, icon.getV1)
        r.addVertex(stack.last.pose, 1, 0, 1).setUv(icon.getU1, icon.getV0)
        r.addVertex(stack.last.pose, 0, 0, 1).setUv(icon.getU0, icon.getV0)

        r.addVertex(stack.last.pose, 1, 1, 1).setUv(icon.getU0, icon.getV1)
        r.addVertex(stack.last.pose, 1, 1, 0).setUv(icon.getU1, icon.getV1)
        r.addVertex(stack.last.pose, 1, 0, 0).setUv(icon.getU1, icon.getV0)
        r.addVertex(stack.last.pose, 1, 0, 1).setUv(icon.getU0, icon.getV0)

        r.addVertex(stack.last.pose, 0, 1, 0).setUv(icon.getU0, icon.getV1)
        r.addVertex(stack.last.pose, 0, 1, 1).setUv(icon.getU1, icon.getV1)
        r.addVertex(stack.last.pose, 0, 0, 1).setUv(icon.getU1, icon.getV0)
        r.addVertex(stack.last.pose, 0, 0, 0).setUv(icon.getU0, icon.getV0)
      }

      stack.popPose()
    }

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}