package li.cil.oc.client.renderer.entity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.Settings
import li.cil.oc.common.entity.Drone
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.{ModelLayerLocation, ModelPart, PartPose}
import net.minecraft.client.model.geom.builders.{CubeListBuilder, LayerDefinition, MeshDefinition, PartDefinition}
import net.minecraft.resources.ResourceLocation

/**
 * 四轴无人机模型（对应 1.7.10 的 `ModelQuadcopter extends ModelBase`）。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - `ModelBase` + `ModelRenderer`（可变盒子列表、`setTextureOffset(name, u, v)`、
 *    `addBox(name, ...)`）→ [[EntityModel]] + [[ModelPart]] + [[LayerDefinition]]。
 *    新体系里网格用 [[MeshDefinition]] / [[PartDefinition]] / [[CubeListBuilder]]
 *    声明一次并烘焙成 `ModelPart` 树：贴图偏移写在 [[CubeListBuilder]] 上，
 *    旋转写在 [[PartPose]] 上，因此 1.7.10 里「一个 ModelRenderer 内放多个
 *    rotateAngleY 不同的盒子」必须拆成多个子部件。
 *  - `render(entity, ...)` 里的 `GL11.glTranslatef/glRotatef` 在 1.21.1 属于渲染器
 *    （[[DroneRenderer]]）的职责：模型只负责设定部件的旋转角（[[setupAnim]]）。
 *  - `GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE)`（指示灯加法混合）在 1.21.1 由
 *    `RenderType` 决定，因此指示灯拆成独立部件（`light0` 到 `light3`），
 *    由渲染器用自发光通道二次绘制。
 *
 * ==保留的对外部件名==
 * `body` / `wing0` 到 `wing3` / `light0` 到 `light3`，与 1.7.10 一致，
 * 便于按旧代码检索。
 */
final class ModelQuadcopter(val root: ModelPart) extends EntityModel[Drone] {

  /** 贴图位置（原 `ModelQuadcopter#texture`）。 */
  val texture: ResourceLocation = ModelQuadcopter.Texture

  // ----------------------------------------------------------------------- //
  // 部件（名字与 1.7.10 一致）
  // ----------------------------------------------------------------------- //

  val body: ModelPart = root.getChild("body")

  val wing0: ModelPart = root.getChild("wing0")
  val wing1: ModelPart = root.getChild("wing1")
  val wing2: ModelPart = root.getChild("wing2")
  val wing3: ModelPart = root.getChild("wing3")

  val light0: ModelPart = root.getChild("light0")
  val light1: ModelPart = root.getChild("light1")
  val light2: ModelPart = root.getChild("light2")
  val light3: ModelPart = root.getChild("light3")

  /** 库存渲染用的静态倾角（原无参 `render()` 里写死的 2 度）。 */
  private val staticTilt = math.toRadians(2).toFloat

  /**
   * 每帧的姿态更新。
   *
   * 1.7.10 在 `doRender` 里直接改 `wingN.rotateAngleX/Z`，1.21.1 统一放在这里：
   *  - 机翼角来自 [[Drone#flapAngles]]。
   *  - 注意 [[Drone#isRunning]] 在客户端恒为 `false`（客户端的 `machine` 是 `null`），
   *    因此这里改读同步字段 `Drone.DataRunning`。同时说明：`common/entity/Drone`
   *    的客户端动画分支目前也用 `isRunning` 判断，所以 `flapAngles` 实际上不会变化，
   *    这里补一层基于 `ageInTicks` 的轻微摆动，保证无人机动起来（降级，不报错）。
   */
  override def setupAnim(drone: Drone,
                         limbSwing: Float,
                         limbSwingAmount: Float,
                         ageInTicks: Float,
                         netHeadYaw: Float,
                         headPitch: Float): Unit = {
    val running = ModelQuadcopter.isRunning(drone)
    val idle = if (running) 0.03f else 0f
    val t = ageInTicks * 0.2f

    wing0.xRot = drone.flapAngles(0)(0) + idle * math.sin(t).toFloat
    wing0.zRot = drone.flapAngles(0)(1) + idle * math.cos(t).toFloat
    wing1.xRot = drone.flapAngles(1)(0) + idle * math.sin(t + 1.3f).toFloat
    wing1.zRot = drone.flapAngles(1)(1) + idle * math.cos(t + 1.3f).toFloat
    wing2.xRot = drone.flapAngles(2)(0) + idle * math.sin(t + 2.6f).toFloat
    wing2.zRot = drone.flapAngles(2)(1) + idle * math.cos(t + 2.6f).toFloat
    wing3.xRot = drone.flapAngles(3)(0) + idle * math.sin(t + 3.9f).toFloat
    wing3.zRot = drone.flapAngles(3)(1) + idle * math.cos(t + 3.9f).toFloat

    syncLightRotations()
  }

  /**
   * 静态姿态（物品栏 / 手持渲染用，原无参 `render()`）。
   *
   * 1.7.10 里这是给 `ItemRenderer` 画「物品形态的无人机」用的：
   * 四个机翼各偏 2 度，看起来像悬停。
   */
  def setupStaticAnim(): Unit = {
    wing0.xRot = staticTilt
    wing0.zRot = staticTilt
    wing1.xRot = -staticTilt
    wing1.zRot = staticTilt
    wing2.xRot = -staticTilt
    wing2.zRot = -staticTilt
    wing3.xRot = staticTilt
    wing3.zRot = -staticTilt

    syncLightRotations()
  }

  private def syncLightRotations(): Unit = {
    light0.xRot = wing0.xRot
    light0.zRot = wing0.zRot
    light1.xRot = wing1.xRot
    light1.zRot = wing1.zRot
    light2.xRot = wing2.xRot
    light2.zRot = wing2.zRot
    light3.xRot = wing3.xRot
    light3.zRot = wing3.zRot
  }

  /**
   * 机体 + 机翼。指示灯**不在**这里画：1.21.1 的混合模式由
   * [[net.minecraft.client.renderer.RenderType]] 决定，加法混合必须换一个缓冲区，
   * 因此交给 [[renderLights]] 单独一遍。
   */
  override def renderToBuffer(poseStack: PoseStack,
                              buffer: VertexConsumer,
                              packedLight: Int,
                              packedOverlay: Int,
                              color: Int): Unit = {
    val lights = Array(light0, light1, light2, light3)
    val saved = lights.map(_.visible)
    lights.foreach(_.visible = false)
    root.render(poseStack, buffer, packedLight, packedOverlay, color)
    var i = 0
    while (i < lights.length) {
      lights(i).visible = saved(i)
      i += 1
    }
  }

  /**
   * 只画四个指示灯（对应 1.7.10 的加法混合 + `glColor3ub(lightColor)`）。
   *
   * 调用方应当使用自发光通道（例如 `RenderType#entityTranslucentEmissive`），
   * 并把 `color` 传成 `0xFF000000 | drone.lightColor`。
   */
  def renderLights(poseStack: PoseStack,
                   buffer: VertexConsumer,
                   packedLight: Int,
                   packedOverlay: Int,
                   color: Int): Unit = {
    val lights = Array(light0, light1, light2, light3)
    val saved = lights.map(_.visible)
    lights.foreach(_.visible = true)
    lights.foreach(_.render(poseStack, buffer, packedLight, packedOverlay, color))
    var i = 0
    while (i < lights.length) {
      lights(i).visible = saved(i)
      i += 1
    }
  }
}

object ModelQuadcopter {
  /** 模型贴图（原 `textures/model/drone.png`）。 */
  val Texture: ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/model/drone.png")

  /**
   * 模型层位置。
   *
   * 1.21.1 的 `EntityModel` 必须先在 `EntityRenderersEvent.RegisterLayerDefinitions`
   * 里登记 [[LayerDefinition]]，再由 `EntityRendererProvider.Context#bakeLayer` 烘焙。
   */
  val Layer: ModelLayerLocation =
    new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "drone"), "main")

  /** 全亮光照值：指示灯是自发光面。 */
  val FullBright: Int = 0xF000E0

  private val TextureWidth = 64
  private val TextureHeight = 32

  /** 1.7.10 里 `body` 的三个盒子都绕 Y 轴转 45 度。 */
  private val Diagonal = math.toRadians(45).toFloat

  /**
   * 是否正在运行。
   *
   * 1.7.10 直接读 `drone.isRunning`；1.21.1 的 `Drone#isRunning` 在客户端恒为 `false`
   * （客户端的 `machine` 是 `null`），运行标志改由同步字段 `Drone.DataRunning` 承载，
   * 因此这里读同步数据。
   */
  def isRunning(drone: Drone): Boolean =
    drone != null && drone.getEntityData.get(Drone.DataRunning).intValue != 0

  /**
   * 构建模型层（原 1.7.10 构造器里的 `setTextureOffset` + `addBox` 序列）。
   *
   * 层级：root 下有 body（top / middle / bottom）、wing0 到 wing3（各含 flap 与 pin）、
   * 以及 light0 到 light3 四片灯光面。
   */
  def createLayer(): LayerDefinition = {
    val mesh = new MeshDefinition()
    val root = mesh.getRoot

    // 机体：三个盒子各自绕 Y 轴转 45 度，1.21.1 里旋转属于 PartPose，因此各自独立成子部件。
    val body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO)
    body.addOrReplaceChild("top",
      CubeListBuilder.create().texOffs(0, 1).addBox(-3f, 1f, -3f, 6f, 1f, 6f),
      PartPose.rotation(0, Diagonal, 0))
    body.addOrReplaceChild("middle",
      CubeListBuilder.create().texOffs(0, 23).addBox(-1f, 0f, -1f, 2f, 1f, 2f),
      PartPose.rotation(0, Diagonal, 0))
    body.addOrReplaceChild("bottom",
      CubeListBuilder.create().texOffs(0, 17).addBox(-2f, -1f, -2f, 4f, 1f, 4f),
      PartPose.rotation(0, Diagonal, 0))

    // 机翼：每片机翼 = 叶片 + 引脚，贴图偏移不同，同样是两个子部件。
    addWing(root, "wing0", "flap0", "pin0", 1f, 0f, -7f, 2f, -1f, -3f)
    addWing(root, "wing1", "flap1", "pin1", 1f, 0f, 1f, 2f, -1f, 2f)
    addWing(root, "wing2", "flap2", "pin2", -7f, 0f, 1f, -3f, -1f, 2f)
    addWing(root, "wing3", "flap3", "pin3", -7f, 0f, -7f, -3f, -1f, -3f)

    // 指示灯：与对应机翼叶片同形，但取 24,0 处的贴图。
    addLight(root, "light0", 1f, 0f, -7f)
    addLight(root, "light1", 1f, 0f, 1f)
    addLight(root, "light2", -7f, 0f, 1f)
    addLight(root, "light3", -7f, 0f, -7f)

    LayerDefinition.create(mesh, TextureWidth, TextureHeight)
  }

  /** 一片机翼：`flap` 是叶片（贴图 0,9），`pin` 是连接轴（贴图 0,27）。 */
  private def addWing(root: PartDefinition,
                      wingName: String,
                      flapName: String,
                      pinName: String,
                      flapX: Float, flapY: Float, flapZ: Float,
                      pinX: Float, pinY: Float, pinZ: Float): Unit = {
    val wing = root.addOrReplaceChild(wingName, CubeListBuilder.create(), PartPose.ZERO)
    wing.addOrReplaceChild(flapName,
      CubeListBuilder.create().texOffs(0, 9).addBox(flapX, flapY, flapZ, 6f, 1f, 6f),
      PartPose.ZERO)
    wing.addOrReplaceChild(pinName,
      CubeListBuilder.create().texOffs(0, 27).addBox(pinX, pinY, pinZ, 1f, 3f, 1f),
      PartPose.ZERO)
  }

  /** 一片指示灯光面（贴图 24,0）。 */
  private def addLight(root: PartDefinition,
                       name: String,
                       x: Float, y: Float, z: Float): Unit = {
    root.addOrReplaceChild(name,
      CubeListBuilder.create().texOffs(24, 0).addBox(x, y, z, 6f, 1f, 6f),
      PartPose.ZERO)
  }
}
