package li.cil.oc.client.renderer.item

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import li.cil.oc.Settings
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.renderer.LightTexture
import net.minecraft.world.entity.LivingEntity
import net.minecraft.resources.ResourceLocation

object HoverBootRenderer {
  val texture = ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/model/drone.png")

  def createBodyLayer(): LayerDefinition = {
    val mesh = new MeshDefinition()
    val root = mesh.getRoot

    val rightLeg = root.addOrReplaceChild("right_leg",
      CubeListBuilder.create(),
      PartPose.offset(-1.9f, 12f, 0f))

    val leftLeg = root.addOrReplaceChild("left_leg",
      CubeListBuilder.create(),
      PartPose.offset(1.9f, 12f, 0f))

    val bootRight = rightLeg.addOrReplaceChild("boot_right",
      CubeListBuilder.create(),
      PartPose.offset(0f, 10.1f, 0f))

    val bootLeft = leftLeg.addOrReplaceChild("boot_left",
      CubeListBuilder.create(),
      PartPose.offset(0f, 10.11f, 0f))

    def addDroneBody(parent: net.minecraft.client.model.geom.builders.PartDefinition, name: String): Unit = {
      parent.addOrReplaceChild(name,
        CubeListBuilder.create()
          .texOffs(0, 23).addBox(-3, 1, -3, 6, 1, 6)
          .texOffs(0, 1) .addBox(-1, 0, -1, 2, 1, 2)
          .texOffs(0, 17).addBox(-2, -1, -2, 4, 1, 4),
        PartPose.offsetAndRotation(0, 0, 0, 0, math.toRadians(45).toFloat, 0))
    }
    addDroneBody(bootLeft,  "drone_body_left")
    addDroneBody(bootRight, "drone_body_right")

    val wing0 = bootLeft.addOrReplaceChild("wing0",
      CubeListBuilder.create()
        .texOffs(0, 9) .addBox(-1, 0, -7, 6, 1, 6)
        .texOffs(0, 27).addBox(0, -1, -3, 1, 3, 1),
      PartPose.ZERO)

    val wing1 = bootLeft.addOrReplaceChild("wing1",
      CubeListBuilder.create()
        .texOffs(0, 9) .addBox(-1, 0, 1, 6, 1, 6)
        .texOffs(0, 27).addBox(0, -1, 2, 1, 3, 1),
      PartPose.ZERO)

    val wing2 = bootRight.addOrReplaceChild("wing2",
      CubeListBuilder.create()
        .texOffs(0, 9) .addBox(-5, 0, 1, 6, 1, 6)
        .texOffs(0, 27).addBox(-1, -1, 2, 1, 3, 1),
      PartPose.ZERO)

    val wing3 = bootRight.addOrReplaceChild("wing3",
      CubeListBuilder.create()
        .texOffs(0, 9) .addBox(-5, 0, -7, 6, 1, 6)
        .texOffs(0, 27).addBox(-1, -1, -3, 1, 3, 1),
      PartPose.ZERO)

    wing0.addOrReplaceChild("light0",
      CubeListBuilder.create().texOffs(24, 0).addBox(-1, 0, -7, 6, 1, 6), PartPose.ZERO)
    wing1.addOrReplaceChild("light1",
      CubeListBuilder.create().texOffs(24, 0).addBox(-1, 0, 1, 6, 1, 6),  PartPose.ZERO)
    wing2.addOrReplaceChild("light2",
      CubeListBuilder.create().texOffs(24, 0).addBox(-5, 0, 1, 6, 1, 6),  PartPose.ZERO)
    wing3.addOrReplaceChild("light3",
      CubeListBuilder.create().texOffs(24, 0).addBox(-5, 0, -7, 6, 1, 6), PartPose.ZERO)

    LayerDefinition.create(mesh, 64, 32)
  }
}

class HoverBootRenderer(root: ModelPart) extends HumanoidModel[LivingEntity](root) {
  private val bootLeft  = leftLeg.getChild("boot_left")
  private val bootRight = rightLeg.getChild("boot_right")

  private val light0 = bootLeft.getChild("wing0").getChild("light0")
  private val light1 = bootLeft.getChild("wing1").getChild("light1")
  private val light2 = bootRight.getChild("wing2").getChild("light2")
  private val light3 = bootRight.getChild("wing3").getChild("light3")

  private val allLightParts = Seq(light0, light1, light2, light3)

  var lightColor: Int = 0x66DD55

  head.visible     = false
  hat.visible      = false
  body.visible     = false
  rightArm.visible = false
  leftArm.visible  = false

  override def setupAnim(entity: LivingEntity, f1: Float, f2: Float, f3: Float, f4: Float, f5: Float): Unit = {
    super.setupAnim(entity, f1, f2, f3, f4, f5)
    crouching = entity.isCrouching
    young     = false
  }

  override def renderToBuffer(
                               poseStack: PoseStack,
                               consumer: VertexConsumer,
                               light: Int,
                               overlay: Int,
                               color: Int
                             ): Unit = {
    allLightParts.foreach(_.visible = false)
    super.renderToBuffer(poseStack, consumer, light, overlay, color)
    allLightParts.foreach(_.visible = true)

    val a  = ((color >>> 24) & 0xFF) / 255f
    val r  = ((color >>> 16) & 0xFF) / 255f
    val g  = ((color >>>  8) & 0xFF) / 255f
    val b  = ((color >>>  0) & 0xFF) / 255f

    val rm = ((lightColor >>> 16) & 0xFF) / 255f
    val gm = ((lightColor >>>  8) & 0xFF) / 255f
    val bm = ((lightColor >>>  0) & 0xFF) / 255f

    val fullBright = LightTexture.pack(15, 15)

    def lightPackedColor(): Int = {
      val rr = ((r * rm) * 255).toInt & 0xFF
      val gg = ((g * gm) * 255).toInt & 0xFF
      val bb = ((b * bm) * 255).toInt & 0xFF
      val aa = (a * 255).toInt & 0xFF
      (aa << 24) | (rr << 16) | (gg << 8) | bb
    }
    val lc = lightPackedColor()

    poseStack.pushPose()
    leftLeg.translateAndRotate(poseStack)
    bootLeft.translateAndRotate(poseStack)
    bootLeft.getChild("wing0").translateAndRotate(poseStack)
    light0.render(poseStack, consumer, fullBright, overlay, lc)
    poseStack.popPose()

    poseStack.pushPose()
    leftLeg.translateAndRotate(poseStack)
    bootLeft.translateAndRotate(poseStack)
    bootLeft.getChild("wing1").translateAndRotate(poseStack)
    light1.render(poseStack, consumer, fullBright, overlay, lc)
    poseStack.popPose()

    poseStack.pushPose()
    rightLeg.translateAndRotate(poseStack)
    bootRight.translateAndRotate(poseStack)
    bootRight.getChild("wing2").translateAndRotate(poseStack)
    light2.render(poseStack, consumer, fullBright, overlay, lc)
    poseStack.popPose()

    poseStack.pushPose()
    rightLeg.translateAndRotate(poseStack)
    bootRight.translateAndRotate(poseStack)
    bootRight.getChild("wing3").translateAndRotate(poseStack)
    light3.render(poseStack, consumer, fullBright, overlay, lc)
    poseStack.popPose()
  }
}