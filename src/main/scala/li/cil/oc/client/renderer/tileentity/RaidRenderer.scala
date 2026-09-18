package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.Raid
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer => TileEntityRenderer}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction

object RaidRenderer extends BlockEntityRendererProvider[Raid] {
  override def create(ctx: BlockEntityRendererProvider.Context): RaidRenderer =
    new RaidRenderer()
}

class RaidRenderer extends TileEntityRenderer[Raid] {
  override def render(
                       raid: Raid,
                       dt: Float,
                       stack: PoseStack,
                       buffer: MultiBufferSource,
                       light: Int,
                       overlay: Int
                     ): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering (aka: wasntme)")

    RenderSystem.setShaderColor(1, 1, 1, 1)

    stack.pushPose()

    stack.translate(0.5, 0.5, 0.5)

    raid.yaw match {
      case Direction.WEST  => stack.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST  => stack.mulPose(Axis.YP.rotationDegrees(90))
      case _               => // No yaw.
    }

    stack.translate(-0.5, 0.5, 0.505)
    RenderState.mirrorScale(stack, 1, -1, 1)

    val r: VertexConsumer = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)

    {
      val icon = Textures.getSprite(Textures.Block.RaidFrontError)
      for (slot <- 0 until raid.getContainerSize) {
        if (!raid.presence(slot)) {
          renderSlot(stack, r, slot, icon)
        }
      }
    }

    {
      val icon = Textures.getSprite(Textures.Block.RaidFrontActivity)
      for (slot <- 0 until raid.getContainerSize) {
        if (
          System.currentTimeMillis() - raid.lastAccess < 400 &&
            raid.getEnvironmentLevel.random.nextDouble() > 0.1 &&
            slot == raid.lastAccess % raid.getContainerSize
        ) {
          renderSlot(stack, r, slot, icon)
        }
      }
    }

    stack.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }

  private val u1 = 2 / 16f
  private val fs = 4 / 16f

  private def renderSlot(stack: PoseStack, r: VertexConsumer, slot: Int, icon: TextureAtlasSprite): Unit = {
    val l = u1 + slot * fs
    val h = u1 + (slot + 1) * fs
    r.addVertex(stack.last.pose, l, 1, 0).setUv(icon.getU(l), icon.getV1)
    r.addVertex(stack.last.pose, h, 1, 0).setUv(icon.getU(h), icon.getV1)
    r.addVertex(stack.last.pose, h, 0, 0).setUv(icon.getU(h), icon.getV0)
    r.addVertex(stack.last.pose, l, 0, 0).setUv(icon.getU(l), icon.getV0)
  }
}
