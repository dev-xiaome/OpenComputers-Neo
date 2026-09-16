package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.DiskDrive
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemDisplayContext

object DiskDriveRenderer extends BlockEntityRendererProvider[DiskDrive] {
  override def create(ctx: BlockEntityRendererProvider.Context): DiskDriveRenderer =
    new DiskDriveRenderer()
}

class DiskDriveRenderer extends BlockEntityRenderer[DiskDrive] {
  private lazy val itemRenderer = Minecraft.getInstance().getItemRenderer

  override def render(drive: DiskDrive, dt: Float, matrix: PoseStack, buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering")

    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

    matrix.pushPose()

    matrix.translate(0.5, 0.5, 0.5)

    drive.yaw match {
      case Direction.WEST => matrix.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => matrix.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST => matrix.mulPose(Axis.YP.rotationDegrees(90))
      case _ => // No yaw.
    }

    drive.items(0) match {
      case stack if !stack.isEmpty =>
        matrix.pushPose()
        matrix.translate(0, 3.5 / 16.0, 6.0 / 16.0)
        matrix.mulPose(Axis.XN.rotationDegrees(90))
        matrix.scale(0.5f, 0.5f, 0.5f)

        val itemLight = LevelRenderer.getLightColor(drive.getLevel, drive.getBlockPos.relative(drive.facing))

        itemRenderer.renderStatic(
          stack,
          ItemDisplayContext.FIXED,
          itemLight,
          overlay,
          matrix,
          buffer,
          drive.getLevel,
          0
        )

        matrix.popPose()
      case _ =>
    }

    if (System.currentTimeMillis() - drive.lastAccess < 400 && drive.getLevel.random.nextDouble() > 0.1) {
      matrix.translate(-0.5, 0.5, 0.505)
      RenderState.mirrorScale(matrix, 1.0f, -1.0f, 1.0f)

      val r = buffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
      val icon = Textures.getSprite(Textures.Block.DiskDriveFrontActivity)
      val pose = matrix.last.pose

      r.addVertex(pose, 0, 1, 0).setUv(icon.getU0, icon.getV1)
      r.addVertex(pose, 1, 1, 0).setUv(icon.getU1, icon.getV1)
      r.addVertex(pose, 1, 0, 0).setUv(icon.getU1, icon.getV0)
      r.addVertex(pose, 0, 0, 0).setUv(icon.getU0, icon.getV0)
    }

    matrix.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}