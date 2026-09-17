package li.cil.oc.client.renderer.item

import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.model.{HumanoidModel, Model}
import net.minecraft.client.model.geom.{ModelLayerLocation, ModelPart, PartPose}
import net.minecraft.client.model.geom.builders.{CubeListBuilder, LayerDefinition, MeshDefinition, PartDefinition}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.{EquipmentSlot, LivingEntity}
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions

/**
 * 「悬浮靴」的护甲外观渲染（对应 1.7.10 的 `object HoverBootRenderer extends ModelBiped`）。
 *
 * ==属于哪一类扩展点==
 * 1.7.10 的 `HoverBootRenderer` 是 `common.item.HoverBoots#getArmorModel` 的返回值，
 * 也就是**穿在身上的护甲模型**（不是手持物品的渲染器），因此在 1.21.1 里对应的
 * 扩展点是 [[IClientItemExtensions]] 的 `getHumanoidArmorModel` /
 * `setupModelAnimations`，而**不是** `BlockEntityWithoutLevelRenderer`。
 * 注册入口见 [[ItemRenderer.registerExtensions]]。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - `ModelBiped` → [[HumanoidModel]]：部件名固定为 `head` / `hat` / `body` /
 *    `right_arm` / `left_arm` / `right_leg` / `left_leg`，由 `HumanoidArmorLayer`
 *    按装备槽设置可见性（`FEET` 只开两条腿），因此靴子几何体挂在腿下面即可自动跟随。
 *  - 1.7.10 里用 `bipedLeftLeg.cubeList.clear()` 清掉原版腿的盒子；
 *    1.21.1 的 `ModelPart` 烘焙后不可改，因此改成「腿部件本身不放任何盒子」，
 *    只作为靴子部件的父节点。
 *  - 1.7.10 里同一个 `body` 实例同时挂到左右两只靴子上；1.21.1 的 `ModelPart`
 *    是严格树结构，不能共享节点，因此拆成 `bodyLeft` / `bodyRight` 两份。
 *  - 自定义的 `LightModelRenderer`（加法混合的灯光面）在 1.21.1 无法在
 *    `Model#renderToBuffer` 内部切换混合模式，见下面的降级说明。
 *
 * ==降级说明==
 *  - 灯光面的加法混合无法实现：`HumanoidArmorLayer` 统一用
 *    `RenderType#armorCutoutNoCull` 渲染整个护甲模型，模型内部拿不到
 *    `MultiBufferSource`，因此灯光面只能当作普通几何体画出来。
 *    要做到 1.7.10 的效果需要给玩家渲染器加一个自定义 `RenderLayer`。
 *  - 贴图仍是悬浮靴所用的护甲材质（`ArmorMaterials.DIAMOND`）对应的
 *    `diamond_layer_1`，而不是这里的 `textures/model/drone.png`：
 *    1.21.1 的护甲贴图由 `Item#getArmorTexture` 决定，`IClientItemExtensions`
 *    没有对应钩子，需要在 `common/item/HoverBoots` 里覆写该方法（超出本次改动范围）。
 */
object HoverBootRenderer {
  /** 贴图位置（原 `HoverBootRenderer.texture`）。 */
  val texture: ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/model/drone.png")

  /** 模型层位置；需要在 `EntityRenderersEvent.RegisterLayerDefinitions` 里登记。 */
  val Layer: ModelLayerLocation =
    new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "hover_boots"), "main")

  /** 指示灯颜色（原 `var lightColor = 0x66DD55`）。 */
  var lightColor: Int = 0x66DD55

  /** 1.7.10 里机体三个盒子共同的 45 度 Y 轴旋转。 */
  private val Diagonal = math.toRadians(45).toFloat

  /**
   * 物品扩展点：把这个扩展注册给悬浮靴以后，玩家穿着它时就会用本模型绘制。
   *
   * 由 [[ItemRenderer.registerExtensions]] 调用。
   */
  def extensions(): IClientItemExtensions = new HoverBootItemExtensions

  /**
   * 模型层定义。
   *
   * 只给两条腿挂靴子几何体，其余人形部件留空（但必须存在，否则
   * [[HumanoidModel]] 的构造器取不到子部件）。
   */
  def createLayer(): LayerDefinition = {
    val mesh = new MeshDefinition()
    val root = mesh.getRoot

    // 其余部件留空：只为了满足 HumanoidModel 的部件名约定。
    root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO)
    root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO)
    root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO)
    root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5f, 2f, 0f))
    root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5f, 2f, 0f))

    // 腿也留空，只当父节点；偏移沿用原版人形模型，保证靴子落在脚的位置。
    val rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12f, 0f))
    val leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12f, 0f))

    // 原 `bootLeft.offsetY = 10.11f / 16f`、`bootRight.offsetY = 10.1f / 16f`：
    // 1.7.10 的 `offsetX/Y/Z` 是方块单位，而 1.21.1 的 `PartPose` 用模型像素，
    // 因此这里换算成 10.11 / 10.1 像素。
    addBoot(leftLeg, "bootLeft", 10.11f, 0, 1)
    addBoot(rightLeg, "bootRight", 10.1f, 2, 3)

    LayerDefinition.create(mesh, 64, 32)
  }

  /**
   * 一只靴子：机体 + 两片机翼（每片机翼带一片灯光面）。
   *
   * `wingA` / `wingB` 是 1.7.10 里的机翼编号（左靴 0/1，右靴 2/3），
   * 保留这个编号是为了和旧代码对照。
   */
  private def addBoot(leg: PartDefinition, name: String, offsetY: Float, wingA: Int, wingB: Int): Unit = {
    val boot = leg.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(0f, offsetY, 0f))

    val body = boot.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO)
    body.addOrReplaceChild("top",
      CubeListBuilder.create().texOffs(0, 1).addBox(-3f, 1f, -3f, 6f, 1f, 6f),
      PartPose.rotation(0, Diagonal, 0))
    body.addOrReplaceChild("middle",
      CubeListBuilder.create().texOffs(0, 23).addBox(-1f, 0f, -1f, 2f, 1f, 2f),
      PartPose.rotation(0, Diagonal, 0))
    body.addOrReplaceChild("bottom",
      CubeListBuilder.create().texOffs(0, 17).addBox(-2f, -1f, -2f, 4f, 1f, 4f),
      PartPose.rotation(0, Diagonal, 0))

    // 1.7.10：wing0 叶片 (-1,0,-7)、引脚 (0,-1,-3)；wing1 叶片 (-1,0,1)、引脚 (0,-1,2)；
    //        wing2 叶片 (-5,0,1)、引脚 (-1,-1,2)；wing3 叶片 (-5,0,-7)、引脚 (-1,-1,-3)。
    // 这里按编号查表。
    val shapes = Array(
      (-1f, 0f, -7f, 0f, -1f, -3f),
      (-1f, 0f, 1f, 0f, -1f, 2f),
      (-5f, 0f, 1f, -1f, -1f, 2f),
      (-5f, 0f, -7f, -1f, -1f, -3f)
    )
    addWing(boot, wingA, shapes(wingA))
    addWing(boot, wingB, shapes(wingB))
  }

  /** 一片机翼：`flap` 是叶片（贴图 0,9），`pin` 是连接轴（贴图 0,27），并附带灯光面。 */
  private def addWing(boot: PartDefinition, index: Int, shape: (Float, Float, Float, Float, Float, Float)): Unit = {
    val (flapX, flapY, flapZ, pinX, pinY, pinZ) = shape
    val wing = boot.addOrReplaceChild("wing" + index, CubeListBuilder.create(), PartPose.ZERO)
    wing.addOrReplaceChild("flap" + index,
      CubeListBuilder.create().texOffs(0, 9).addBox(flapX, flapY, flapZ, 6f, 1f, 6f),
      PartPose.ZERO)
    wing.addOrReplaceChild("pin" + index,
      CubeListBuilder.create().texOffs(0, 27).addBox(pinX, pinY, pinZ, 1f, 3f, 1f),
      PartPose.ZERO)
    // 灯光面：与叶片同形，贴图偏移 24,0（原 `LightModelRenderer`）。
    wing.addOrReplaceChild("light" + index,
      CubeListBuilder.create().texOffs(24, 0).addBox(flapX, flapY, flapZ, 6f, 1f, 6f),
      PartPose.ZERO)
  }
}

/**
 * 悬浮靴的护甲模型。
 *
 * 继承 [[HumanoidModel]] 而不是裸的 `EntityModel`，是因为 `HumanoidArmorLayer`
 * 会把玩家本体模型的腿部姿态（`copyPropertiesTo` 到 `ModelPart#copyFrom`）
 * 复制过来，只有人形模型才能接收到这份姿态，靴子也才会跟着腿摆动。
 */
final class HoverBootModel(root: ModelPart) extends HumanoidModel[LivingEntity](root) {

  val bootLeft: ModelPart = root.getChild("left_leg").getChild("bootLeft")
  val bootRight: ModelPart = root.getChild("right_leg").getChild("bootRight")

  val bodyLeft: ModelPart = bootLeft.getChild("body")
  val bodyRight: ModelPart = bootRight.getChild("body")

  val wing0: ModelPart = bootLeft.getChild("wing0")
  val wing1: ModelPart = bootLeft.getChild("wing1")
  val wing2: ModelPart = bootRight.getChild("wing2")
  val wing3: ModelPart = bootRight.getChild("wing3")

  val light0: ModelPart = wing0.getChild("light0")
  val light1: ModelPart = wing1.getChild("light1")
  val light2: ModelPart = wing2.getChild("light2")
  val light3: ModelPart = wing3.getChild("light3")

  // 1.7.10 用 `bipedHead.isHidden = true` 等隐藏人形本体；
  // 1.21.1 的等价物是 `visible = false`。`HumanoidArmorLayer` 稍后还会按装备槽
  // 再设置一遍可见性（`FEET` 只留两条腿），两处结论一致。
  setAllVisible(false)
  rightLeg.visible = true
  leftLeg.visible = true

  /**
   * 机翼的轻微摆动。
   *
   * 1.7.10 的机翼是静止的（`render` 只同步了 `isSneak`）；1.21.1 没有「每帧调用
   * `setupAnim`」的保证（`HumanoidArmorLayer` 只调用 `setupModelAnimations`），
   * 因此摆动逻辑放在这里，由扩展点驱动。
   */
  def animate(ageInTicks: Float): Unit = {
    val t = ageInTicks * 0.25f
    val a = 0.10f * math.sin(t).toFloat

    wing0.xRot = a
    wing0.zRot = a
    wing1.xRot = -a
    wing1.zRot = a
    wing2.xRot = -a
    wing2.zRot = -a
    wing3.xRot = a
    wing3.zRot = -a

    light0.xRot = wing0.xRot
    light0.zRot = wing0.zRot
    light1.xRot = wing1.xRot
    light1.zRot = wing1.zRot
    light2.xRot = wing2.xRot
    light2.zRot = wing2.zRot
    light3.xRot = wing3.xRot
    light3.zRot = wing3.zRot
  }

  // 1.21.1 的护甲渲染不走 `setupAnim`，这里保持 `HumanoidModel` 的默认实现，
  // 保证模型被别处（例如预览界面）直接使用时仍然有正常的腿部姿态。
  override def setupAnim(entity: LivingEntity,
                         limbSwing: Float,
                         limbSwingAmount: Float,
                         ageInTicks: Float,
                         netHeadYaw: Float,
                         headPitch: Float): Unit = {
    super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)
    animate(ageInTicks)
  }
}

/**
 * 悬浮靴的 [[IClientItemExtensions]]。
 *
 * 模型延迟到第一次渲染时才烘焙：注册事件发生在客户端 setup 期间，
 * 那时 `Minecraft` 实例不一定已经就绪。
 */
private final class HoverBootItemExtensions extends IClientItemExtensions {
  private lazy val model: HoverBootModel =
    new HoverBootModel(Minecraft.getInstance.getEntityModels.bakeLayer(HoverBootRenderer.Layer))

  override def getHumanoidArmorModel(livingEntity: LivingEntity,
                                     itemStack: ItemStack,
                                     equipmentSlot: EquipmentSlot,
                                     original: HumanoidModel[_]): HumanoidModel[_] = {
    // 悬浮靴只占脚部槽；其它槽位不应该拿到这个扩展（那时候身上也不是悬浮靴），
    // 这里再挡一道，避免误伤。
    if (equipmentSlot != EquipmentSlot.FEET) return original
    model
  }

  override def setupModelAnimations(livingEntity: LivingEntity,
                                    itemStack: ItemStack,
                                    equipmentSlot: EquipmentSlot,
                                    armorModel: Model,
                                    limbSwing: Float,
                                    limbSwingAmount: Float,
                                    partialTick: Float,
                                    ageInTicks: Float,
                                    netHeadYaw: Float,
                                    headPitch: Float): Unit = {
    armorModel match {
      case boot: HoverBootModel => boot.animate(ageInTicks)
      case _ =>
    }
  }
}
