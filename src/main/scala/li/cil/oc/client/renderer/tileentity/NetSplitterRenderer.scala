package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.NetSplitter
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.world.inventory.InventoryMenu

class NetSplitterRenderer(ctx: BlockEntityRendererProvider.Context) extends BlockEntityRenderer[NetSplitter] {

  override def render(splitter: NetSplitter, dt: Float, stack: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

    if (splitter.openSides.contains(!splitter.isInverted)) {
      stack.pushPose()

      stack.translate(0.5, 0.5, 0.5)
      RenderState.mirrorScale(stack, 1.0025f, -1.0025f, 1.0025f)
      stack.translate(-0.5, -0.5, -0.5)

      RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS)

      val r = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
      val sideActivity = Textures.getSprite(Textures.Block.NetSplitterOn)
      val matrix = stack.last.pose

      if (splitter.isSideOpen(Direction.DOWN)) {
        r.addVertex(matrix, 0, 1, 0).setUv(sideActivity.getU1, sideActivity.getV0)
        r.addVertex(matrix, 1, 1, 0).setUv(sideActivity.getU0, sideActivity.getV0)
        r.addVertex(matrix, 1, 1, 1).setUv(sideActivity.getU0, sideActivity.getV1)
        r.addVertex(matrix, 0, 1, 1).setUv(sideActivity.getU1, sideActivity.getV1)
      }

      if (splitter.isSideOpen(Direction.UP)) {
        r.addVertex(matrix, 0, 0, 0).setUv(sideActivity.getU1, sideActivity.getV1)
        r.addVertex(matrix, 0, 0, 1).setUv(sideActivity.getU1, sideActivity.getV0)
        r.addVertex(matrix, 1, 0, 1).setUv(sideActivity.getU0, sideActivity.getV0)
        r.addVertex(matrix, 1, 0, 0).setUv(sideActivity.getU0, sideActivity.getV1)
      }

      if (splitter.isSideOpen(Direction.NORTH)) {
        r.addVertex(matrix, 1, 1, 0).setUv(sideActivity.getU0, sideActivity.getV1)
        r.addVertex(matrix, 0, 1, 0).setUv(sideActivity.getU1, sideActivity.getV1)
        r.addVertex(matrix, 0, 0, 0).setUv(sideActivity.getU1, sideActivity.getV0)
        r.addVertex(matrix, 1, 0, 0).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      if (splitter.isSideOpen(Direction.SOUTH)) {
        r.addVertex(matrix, 0, 1, 1).setUv(sideActivity.getU0, sideActivity.getV1)
        r.addVertex(matrix, 1, 1, 1).setUv(sideActivity.getU1, sideActivity.getV1)
        r.addVertex(matrix, 1, 0, 1).setUv(sideActivity.getU1, sideActivity.getV0)
        r.addVertex(matrix, 0, 0, 1).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      if (splitter.isSideOpen(Direction.WEST)) {
        r.addVertex(matrix, 0, 1, 0).setUv(sideActivity.getU0, sideActivity.getV1)
        r.addVertex(matrix, 0, 1, 1).setUv(sideActivity.getU1, sideActivity.getV1)
        r.addVertex(matrix, 0, 0, 1).setUv(sideActivity.getU1, sideActivity.getV0)
        r.addVertex(matrix, 0, 0, 0).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      if (splitter.isSideOpen(Direction.EAST)) {
        r.addVertex(matrix, 1, 1, 1).setUv(sideActivity.getU0, sideActivity.getV1)
        r.addVertex(matrix, 1, 1, 0).setUv(sideActivity.getU1, sideActivity.getV1)
        r.addVertex(matrix, 1, 0, 0).setUv(sideActivity.getU1, sideActivity.getV0)
        r.addVertex(matrix, 1, 0, 1).setUv(sideActivity.getU0, sideActivity.getV0)
      }

      stack.popPose()
    }

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}