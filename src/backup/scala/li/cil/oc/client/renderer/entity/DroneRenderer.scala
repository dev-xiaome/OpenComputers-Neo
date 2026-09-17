package li.cil.oc.client.renderer.entity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.common.entity.Drone
import net.minecraft.client.model.geom.{ModelLayerLocation, ModelPart}
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.client.renderer.entity.{EntityRenderer, EntityRendererProvider}
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import org.joml.Quaternionf

/**
 * 无人机实体渲染器（对应 1.7.10 的 `object DroneRenderer extends Render`）。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - `Render#doRender(entity, x, y, z, yaw, dt)` → [[EntityRenderer#render]]：
 *    渲染器不再自己平移 `x/y/z`（那由 `EntityRenderDispatcher` 完成），
 *    只处理「实体原点之上」的局部变换。
 *  - 全局 `GL11` 矩阵栈 → [[PoseStack]]；`bindEntityTexture` → 通过
 *    `MultiBufferSource#getBuffer(RenderType)` 选通道，贴图位置由
 *    [[getTextureLocation]] 提供。
 *  - `ModelBase#render` → `Model#renderToBuffer`（机体一遍 + 自发光指示灯一遍）。
 *  - 1.21.1 的 `EntityModel` 需要在 `RegisterLayerDefinitions` 里先登记
 *    [[LayerDefinition]]，见 [[DroneRenderer.registerLayerDefinitions]]。
 *  - 原 `object DroneRenderer` 变成了 `class`（1.21.1 的渲染器由
 *    `EntityRendererProvider` 按 `Context` 构造），注册入口见 `client/Proxy.scala`。
 */
class DroneRenderer(context: EntityRendererProvider.Context)
  extends EntityRenderer[Drone](context) {

  /** 无人机模型（原 `DroneRenderer.model`）。 */
  val model: ModelQuadcopter = new ModelQuadcopter(context.bakeLayer(DroneRenderer.Layer))

  // 无人机比玩家小一圈，阴影半径按模型尺寸给。
  this.shadowRadius = 0.25f

  /**
   * 绘制无人机。
   *
   * 对应 1.7.10 `doRender` 里 `glTranslated(x, y + 2/16, z)` 之后的那一段：
   * 悬停抖动、侧飞倾斜、机体自转，最后画模型。
   */
  override def render(drone: Drone,
                      entityYaw: Float,
                      partialTicks: Float,
                      poseStack: PoseStack,
                      buffer: MultiBufferSource,
                      packedLight: Int): Unit = {
    if (drone == null) return

    // 名字标签等由基类处理（1.7.10 里无人机不显示名字标签，这里保持同样的保守做法：
    // 基类只在 `shouldShowName` 为真时绘制，无人机没有自定义名字时不会绘制）。
    super.render(drone, entityYaw, partialTicks, poseStack, buffer, packedLight)

    val running = ModelQuadcopter.isRunning(drone)
    val worldTime = drone.level().getGameTime + partialTicks

    poseStack.pushPose()

    // 原 `glTranslated(x, y + 2/16f, z)`：x/z 由调度器负责，这里只抬高 2 像素。
    poseStack.translate(0.0, 2 / 16.0, 0.0)

    if (running) {
      // 悬停抖动：原 `timeJitter = drone.hashCode() ^ 0xFF`。
      val timeJitter = drone.hashCode() ^ 0xFF
      poseStack.translate(0.0, (math.sin(timeJitter + worldTime / 20.0) * (1 / 16f)).toFloat.toDouble, 0.0)
    }

    // 侧飞：沿「速度 × 上方向」的轴倾斜，倾斜角与相对速度成正比（原 `glRotated`）。
    val velocity = drone.getDeltaMovement
    val speed = velocity.length()
    if (speed > 1.0e-4) {
      val direction = velocity.scale(1.0 / speed)
      val up = new Vec3(0, 1, 0)
      if (direction.dot(up) < 0.99) {
        val axis = direction.cross(up)
        if (axis.lengthSqr() > 1.0e-6) {
          val degrees = -20.0 * speed / math.max(drone.maxVelocity.toDouble, 1.0e-4)
          poseStack.mulPose(new Quaternionf().rotateAxis(
            math.toRadians(degrees).toFloat,
            axis.x.toFloat, axis.y.toFloat, axis.z.toFloat))
        }
      }
    }

    // 机体自转（客户端在 `Drone#tick` 里更新 `bodyAngle`）。
    poseStack.mulPose(Axis.YP.rotationDegrees(drone.bodyAngle))

    val ageInTicks = drone.tickCount + partialTicks
    model.setupAnim(drone, 0f, 0f, ageInTicks, 0f, 0f)

    // 机体 + 机翼。
    val bodyBuffer = buffer.getBuffer(RenderType.entityCutoutNoCull(ModelQuadcopter.Texture))
    model.renderToBuffer(poseStack, bodyBuffer, packedLight, OverlayTexture.NO_OVERLAY, -1)

    if (running) {
      // 指示灯：1.7.10 用加法混合 + `glColor3ub(lightColor)`；
      // 1.21.1 换成自发光通道，颜色通过顶点色传入。
      val lightBuffer = buffer.getBuffer(RenderType.entityTranslucentEmissive(ModelQuadcopter.Texture))
      model.renderLights(poseStack, lightBuffer, ModelQuadcopter.FullBright, OverlayTexture.NO_OVERLAY,
        0xFF000000 | (drone.lightColor & 0xFFFFFF))
    }

    poseStack.popPose()
  }

  override def getTextureLocation(drone: Drone): ResourceLocation = ModelQuadcopter.Texture
}

object DroneRenderer {
  /** 模型层位置（与 [[ModelQuadcopter.Layer]] 同一个实例，供注册与烘焙两端共用）。 */
  val Layer: ModelLayerLocation = ModelQuadcopter.Layer

  /**
   * 登记无人机的模型层定义。
   *
   * 1.7.10 的 `ModelBase` 是直接 `new` 出来的，不需要登记；1.21.1 的
   * [[LayerDefinition]] 必须先通过本事件交给 `EntityModelSet`，
   * 之后 `EntityRendererProvider.Context#bakeLayer` 才能取到 [[ModelPart]]。
   *
   * 由父代理在 `client/Proxy.scala` 里通过
   * `EntityRenderersEvent.RegisterLayerDefinitions` 调用。
   */
  def registerLayerDefinitions(event: EntityRenderersEvent.RegisterLayerDefinitions): Unit = {
    if (event == null) return
    event.registerLayerDefinition(Layer, new java.util.function.Supplier[LayerDefinition] {
      override def get(): LayerDefinition = ModelQuadcopter.createLayer()
    })
  }
}
