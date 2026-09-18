package li.cil.oc.client.renderer.entity

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.equipment.hats.EntityHats
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.{MultiBufferSource, Sheets}
import net.minecraft.world.level.block.Blocks
import li.cil.oc.common.entity.TrainRobot

/** Renders Create's train hat without making the base entity renderer require Create at runtime. */
object CreateTrainRobotHatRenderer {
  def render(entity: TrainRobot, pose: PoseStack, buffer: MultiBufferSource, light: Int): Unit = {
    if (!EntityHats.shouldRenderTrainHat(entity)) return

    pose.pushPose()
    // The robot's chassis is wider than a normal mob head. Keep the hat's
    // point above the chassis while bringing the brim down onto it.
    pose.translate(0.5, 0.86, 0.5)
    pose.scale(0.8f, 0.8f, 0.8f)
    val hat = CachedBuffers.partial(AllPartialModels.TRAIN_HAT, Blocks.AIR.defaultBlockState()).asInstanceOf[SuperByteBuffer]
    hat.disableDiffuse()
    hat.light(light)
    hat.renderInto(pose, buffer.getBuffer(Sheets.cutoutBlockSheet()))
    pose.popPose()
  }
}
