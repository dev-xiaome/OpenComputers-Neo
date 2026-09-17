package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.Geolyzer
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

object GeolyzerRenderer extends BlockEntityRendererProvider[Geolyzer] {
  override def create(ctx: BlockEntityRendererProvider.Context): GeolyzerRenderer =
    new GeolyzerRenderer()
}

class GeolyzerRenderer extends BlockEntityRenderer[Geolyzer] {

  override def render(geolyzer: Geolyzer, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

    stack.pushPose()

    stack.translate(0.5, 0.5, 0.5)
    RenderState.mirrorScale(stack, 1.0025f, -1.0025f, 1.0025f)
    stack.translate(-0.5, -0.5, -0.5)

    val r = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
    val matrix = stack.last.pose

    val icon = Textures.getSprite(Textures.Block.GeolyzerTopOn)
    r.addVertex(matrix, 0, 0, 1).setUv(icon.getU0, icon.getV1)
    r.addVertex(matrix, 1, 0, 1).setUv(icon.getU1, icon.getV1)
    r.addVertex(matrix, 1, 0, 0).setUv(icon.getU1, icon.getV0)
    r.addVertex(matrix, 0, 0, 0).setUv(icon.getU0, icon.getV0)

    stack.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}