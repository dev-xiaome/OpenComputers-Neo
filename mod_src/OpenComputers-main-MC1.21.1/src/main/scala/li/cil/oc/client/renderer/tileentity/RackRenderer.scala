package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.common.blockentity.Rack
import li.cil.oc.common.datacomponents.CompoundStorage
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer => TileEntityRenderer}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.neoforged.neoforge.common.NeoForge

object RackRenderer extends BlockEntityRendererProvider[Rack] {
  override def create(ctx: BlockEntityRendererProvider.Context): RackRenderer =
    new RackRenderer()
}

class RackRenderer extends TileEntityRenderer[Rack] {
  private final val vOffset = 2 / 16f
  private final val vSize   = 3 / 16f

  override def render(
                       rack: Rack,
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

    rack.yaw match {
      case Direction.WEST  => stack.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST  => stack.mulPose(Axis.YP.rotationDegrees(90))
      case _               => // No yaw.
    }

    stack.translate(-0.5, 0.5, 0.505 - 0.5f / 16f)
    RenderState.mirrorScale(stack, 1, -1, 1)

    val rackLight = LevelRenderer.getLightColor(rack.getLevel, rack.getBlockPos.relative(rack.facing))
    for (i <- 0 until rack.getContainerSize) {
      if (!rack.getItem(i).isEmpty) {
        val v0    = vOffset + i * vSize
        val v1    = vOffset + (i + 1) * vSize
        val event = new RackMountableRenderEvent.BlockEntity(rack, i, rack.lastData(i) getOrElse CompoundStorage.EMPTY, stack, buffer, rackLight, overlay, v0, v1)
        NeoForge.EVENT_BUS.post(event)
      }
    }

    stack.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}