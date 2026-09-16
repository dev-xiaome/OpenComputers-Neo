package li.cil.oc.client.renderer.entity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import li.cil.oc.common.entity.Drone
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.{ModelLayerLocation, ModelPart, PartPose}
import net.minecraft.client.model.geom.builders.{CubeListBuilder, LayerDefinition, MeshDefinition}
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import net.minecraft.resources.ResourceLocation

object ModelQuadcopter {
  val LAYER_LOCATION = new ModelLayerLocation(
    ResourceLocation.fromNamespaceAndPath("opencomputers", "drone"), "main")

  def createLayer(): LayerDefinition = {
    val mesh = new MeshDefinition()
    val root = mesh.getRoot

    root.addOrReplaceChild("body", CubeListBuilder.create()
      .texOffs(0, 23).addBox(-3, 1, -3, 6, 1, 6)
      .texOffs(0, 1).addBox(-1, 0, -1, 2, 1, 2)
      .texOffs(0, 17).addBox(-2, -1, -2, 4, 1, 4),
      PartPose.rotation(0, math.toRadians(45).toFloat, 0))

    val wingOffsets = Array((1f, -7f, 2f, -3f), (1f, 1f, 2f, 2f), (-7f, 1f, -3f, 2f), (-7f, -7f, -3f, -3f))
    for (i <- 0 to 3) {
      val (wx, wz, px, pz) = wingOffsets(i)
      root.addOrReplaceChild(s"wing$i", CubeListBuilder.create()
        .texOffs(0, 9).addBox(wx, 0, wz, 6, 1, 6)
        .texOffs(0, 27).addBox(px, -1, pz, 1, 3, 1), PartPose.ZERO)

      root.addOrReplaceChild(s"light$i", CubeListBuilder.create()
        .texOffs(24, 0).addBox(wx, 0, wz, 6, 1, 6), PartPose.ZERO)
    }

    LayerDefinition.create(mesh, 64, 32)
  }
}

final class ModelQuadcopter(root: ModelPart) extends EntityModel[Drone] {
  private val body = root.getChild("body")
  private val wings = Array(
    root.getChild("wing0"), root.getChild("wing1"),
    root.getChild("wing2"), root.getChild("wing3"))
  private val lights = Array(
    root.getChild("light0"), root.getChild("light1"),
    root.getChild("light2"), root.getChild("light3"))

  private val up = new Vec3(0, 1, 0)
  private var cachedEntity: Drone = _
  private var cachedDt = 0.0f

  override def setupAnim(drone: Drone, f1: Float, f2: Float, f3: Float, f4: Float, f5: Float): Unit = {}

  override def prepareMobModel(drone: Drone, f1: Float, f2: Float, dt: Float): Unit = {
    cachedEntity = drone
    cachedDt = dt
  }

  override def renderToBuffer(stack: PoseStack, builder: VertexConsumer, light: Int, overlay: Int, color: Int): Unit = {
    if (cachedEntity != null) {
      doRender(cachedEntity, cachedDt, stack, builder, light, overlay, color)
    }
  }

  private def doRender(drone: Drone, dt: Float, stack: PoseStack, builder: VertexConsumer,
                       light: Int, overlay: Int, color: Int): Unit = {
    val a = ((color >>> 24) & 0xFF) / 255f
    val r = ((color >>> 16) & 0xFF) / 255f
    val g = ((color >>>  8) & 0xFF) / 255f
    val b = ((color >>>  0) & 0xFF) / 255f

    stack.pushPose()

    if (drone.isRunning) {
      val timeJitter = drone.hashCode() ^ 0xFF
      stack.translate(
        0,
        (math.sin(timeJitter + (drone.getEnvironmentLevel.getGameTime + dt) / 20.0) * (1 / 16f)).toFloat,
        0)
    }

    val direction = drone.getDeltaMovement.normalize()
    if (direction.dot(up) < 0.99) {
      val rotationAxis = direction.cross(up)
      val relativeSpeed = drone.getDeltaMovement.length().toFloat / drone.maxVelocity
      val degrees: Float = relativeSpeed * -20.0f
      val rotation = new Quaternionf().setAngleAxis(
        Math.toRadians(degrees).toFloat,
        rotationAxis.x().toFloat,
        rotationAxis.y().toFloat,
        rotationAxis.z().toFloat)
      stack.mulPose(rotation)
    }

    stack.mulPose(Axis.YP.rotationDegrees(drone.bodyAngle))
    body.render(stack, builder, light, overlay, color)

    for (i <- 0 to 3) {
      wings(i).xRot = drone.flapAngles(i)(0)
      wings(i).zRot = drone.flapAngles(i)(1)
      wings(i).render(stack, builder, light, overlay, color)
    }

    if (drone.isRunning) {
      val lightColor = drone.lightColor
      val rr = (r * ((lightColor >>> 16) & 0xFF)).toInt & 0xFF
      val gg = (g * ((lightColor >>>  8) & 0xFF)).toInt & 0xFF
      val bb = (b * ((lightColor >>>  0) & 0xFF)).toInt & 0xFF
      val aa = (a * 255).toInt & 0xFF
      val lightPackedColor = (aa << 24) | (rr << 16) | (gg << 8) | bb
      val fullLight = LightTexture.pack(15, 15)

      for (i <- 0 to 3) {
        lights(i).xRot = drone.flapAngles(i)(0)
        lights(i).zRot = drone.flapAngles(i)(1)
        lights(i).render(stack, builder, fullLight, OverlayTexture.NO_OVERLAY, lightPackedColor)
      }
    }

    stack.popPose()
  }
}