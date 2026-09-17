package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity
import li.cil.oc.util.RenderState
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer => TileEntityRenderer}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

object RelayRenderer extends BlockEntityRendererProvider[blockentity.Relay] {
  override def create(ctx: BlockEntityRendererProvider.Context): RelayRenderer =
    new RelayRenderer()
}

class RelayRenderer extends TileEntityRenderer[blockentity.Relay] {
  override def render(
                       switch: blockentity.Relay,
                       dt: Float,
                       stack: PoseStack,
                       buffer: MultiBufferSource,
                       light: Int,
                       overlay: Int
                     ): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering (aka: wasntme)")

    RenderSystem.setShaderColor(1, 1, 1, 1)

    val activity = math.max(0, 1 - (System.currentTimeMillis() - switch.lastMessage) / 1000.0)
    if (activity > 0) {
      stack.pushPose()

      stack.translate(0.5, 0.5, 0.5)
      RenderState.mirrorScale(stack, 1.0025f, -1.0025f, 1.0025f)
      stack.translate(-0.5f, -0.5f, -0.5f)

      val r = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)

      val icon = Textures.getSprite(Textures.Block.SwitchSideOn)
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

      stack.popPose()
    }

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}