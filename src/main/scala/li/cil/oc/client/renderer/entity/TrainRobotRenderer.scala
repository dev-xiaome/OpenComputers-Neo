package li.cil.oc.client.renderer.entity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.tileentity.RobotRenderer
import li.cil.oc.common.entity.TrainRobot
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.{EntityRenderer, EntityRendererProvider}
import net.minecraft.util.Mth
import com.mojang.math.Axis

import java.lang.reflect.Method

class TrainRobotRenderer(ctx: EntityRendererProvider.Context) extends EntityRenderer[TrainRobot](ctx) {
  shadowRadius = 0f

  private val createHatRenderer: Option[(AnyRef, Method)] = try {
    val companion = Class.forName("li.cil.oc.client.renderer.entity.CreateTrainRobotHatRenderer$")
    val module = companion.getField("MODULE$").get(null).asInstanceOf[AnyRef]
    val method = companion.getMethod(
      "render",
      classOf[TrainRobot],
      classOf[PoseStack],
      classOf[MultiBufferSource],
      java.lang.Integer.TYPE
    )
    Some(module -> method)
  }
  catch {
    case _: Throwable => None
  }

  override def render(entity: TrainRobot,
                      yaw: Float,
                      partialTick: Float,
                      pose: PoseStack,
                      buffer: MultiBufferSource,
                      light: Int): Unit = {
    pose.pushPose()
    val renderYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot)
    pose.mulPose(Axis.YP.rotationDegrees(renderYaw))
    pose.translate(-0.5, 0, -0.5)
    RobotRenderer.renderChassis(
      pose,
      buffer,
      light,
      offset = entity.tickCount + partialTick,
      isRunningOverride = true,
      flag = entity.flag
    )
    createHatRenderer.foreach { case (module, method) =>
      try method.invoke(module, entity, pose, buffer, Int.box(light))
      catch { case _: Throwable => () }
    }
    pose.popPose()
    super.render(entity, yaw, partialTick, pose, buffer, light)
  }

  override def getTextureLocation(entity: TrainRobot) = Textures.Model.Robot
}
