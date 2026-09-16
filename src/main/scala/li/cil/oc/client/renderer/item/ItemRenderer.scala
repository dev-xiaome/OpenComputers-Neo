package li.cil.oc.client.renderer.item

import com.google.common.base.Strings
import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.client.KeyBindings
import li.cil.oc.client.renderer.entity.ModelQuadcopter
import li.cil.oc.client.renderer.tileentity.RenderUtil
import li.cil.oc.common.init.Registry
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.util.Color
import li.cil.oc.util.ExtendedAABB._
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.{BlockEntityWithoutLevelRenderer, MultiBufferSource, RenderType}
import net.minecraft.client.renderer.texture.{MissingTextureAtlasSprite, TextureAtlas, TextureAtlasSprite}
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.{ItemDisplayContext, ItemStack}
import net.neoforged.neoforge.client.extensions.common.{IClientItemExtensions, RegisterClientExtensionsEvent}

/**
 * 需要自定义外观的物品（对应 1.7.10 的 `object ItemRenderer extends IItemRenderer`）。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - `IItemRenderer` / `MinecraftForgeClient.registerItemRenderer(item, renderer)` 这一整套
 *    已经不存在。1.21.1 的入口是 `RegisterClientExtensionsEvent#registerItem`，
 *    自定义渲染器由 [[IClientItemExtensions#getCustomRenderer]] 返回，类型是
 *    [[BlockEntityWithoutLevelRenderer]]，渲染方法签名是
 *    `renderByItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay)`。
 *  - `handleRenderType` / `shouldUseRenderHelper` 没有对应概念：1.21.1 里
 *    「用哪种渲染上下文」由 `ItemDisplayContext` 参数表达，位置调整由
 *    `ItemRenderer` 与模型 JSON 的 `display` 段负责。
 *  - `RenderBlocks` / `Tessellator` / `IIcon` 全部删除，改走
 *    `MultiBufferSource` + `VertexConsumer`，方块图集用 `RenderUtil#sprite`。
 *
 * ==重要前提：自定义渲染器何时生效==
 * `ItemRenderer#render` 只有在**物品的烘焙模型**（`BakedModel#isCustomRenderer`）
 * 为真时才会调用这里的渲染器；对普通物品模型（JSON 的 `parent` 是
 * `minecraft:item/generated` 或某个方块模型，例如本工程的 `floppy.json` /
 * `print.json`）它是 `false`，也就是说下面的自定义渲染器**目前不会被调用**。
 * 要在游戏里真正看到这些自定义外观，需要把对应 item 模型 JSON 的 `parent`
 * 改成 `builtin/entity`（属于资源侧改动，不在本次 scala 代码改动范围内）。
 * 这里把渲染器按 1.21.1 的语义完整实现，注册本身是无害的。
 *
 * ==关于线缆==
 * 1.7.10 之所以给线缆注册物品渲染器，是因为它要给物品形态的线缆画「连接头」。
 * 1.21.1 里线缆方块已经是完整的烘焙模型（`models/block/cable.json` 一带），
 * 物品模型 `cable.json` 直接继承它即可得到同样的外观，因此**不注册**自定义渲染器，
 * 避免将来模型改成 `builtin/entity` 时反而把线缆物品画成空白。
 */
object ItemRenderer {

  /**
   * 注册所有需要自定义外观的物品。
   *
   * 由父代理在 `client/Proxy.scala` 里通过
   * [[RegisterClientExtensionsEvent]] 调用（对应 1.7.10 的
   * `MinecraftForgeClient.registerItemRenderer`）。
   */
  def registerExtensions(event: RegisterClientExtensionsEvent): Unit = {
    if (event == null) return

    // 软盘 / 战利品盘：底图之上叠印文件系统标签（原 `isFloppy` 的两个物品）。
    register(event, Constants.ItemName.Floppy, new FloppyItemExtensions)
    register(event, Constants.ItemName.LootDisk, new FloppyItemExtensions)

    // 打印件：形状由 NBT 决定，烘焙模型表达不了，必须动态画。
    register(event, Constants.BlockName.Print, new PrintItemExtensions)

    // 无人机：原 1.7.10 在 INVENTORY / ENTITY / EQUIPPED 三种上下文里都画四轴模型。
    register(event, Constants.ItemName.Drone, new DroneItemExtensions)

    // 悬浮靴：它是护甲模型（不是手持物品渲染器），走 `IClientItemExtensions` 的
    // `getHumanoidArmorModel`，见 [[HoverBootRenderer]]。
    register(event, Constants.ItemName.HoverBoots, HoverBootRenderer.extensions())

    // 线缆：见类注释，1.21.1 用烘焙模型即可，不注册。
  }

  /** 按物品名取 `Item` 并注册；名字没注册（返回 `null`）时静默跳过。 */
  private def register(event: RegisterClientExtensionsEvent, name: String, extensions: IClientItemExtensions): Unit = {
    val item = Registry.getItem(name)
    if (item == null) return
    event.registerItem(extensions, item)
  }

  // ----------------------------------------------------------------------- //
  // 软盘
  // ----------------------------------------------------------------------- //

  /**
   * 读出软盘的文件系统标签（原 `ItemRenderer#renderItem` 里那段 NBT 查询）。
   *
   * 键名沿用 1.7.10：`data` 段下的 `fs.label`，前缀是 `Settings.namespace`。
   */
  private[item] def floppyLabel(stack: ItemStack): String = {
    if (stack == null || stack.isEmpty) return "disk"
    val nbt = stack.getTag()
    if (nbt != null) {
      val dataKey = Settings.namespace + "data"
      if (nbt.contains(dataKey)) {
        val data = nbt.getCompound(dataKey)
        val labelKey = Settings.namespace + "fs.label"
        if (data.contains(labelKey)) return data.getString(labelKey)
      }
    }
    "disk"
  }

  // ----------------------------------------------------------------------- //
  // 打印件
  // ----------------------------------------------------------------------- //

  /**
   * 打印件为空时用来占位的形状（原 `ItemRenderer.nullShape`）：
   * 整个方块的半透明白色立方体，避免「什么都不画」。
   */
  lazy val nullShape: PrintData.Shape =
    new PrintData.Shape(unitBounds, Settings.resourceDomain + ":White", Some(Color.Lime))

  /**
   * 按 1.7.10 的方式画一个打印形状。
   *
   * 关键差异：1.7.10 用的是 `icon.getInterpolatedU(bounds.minX * 16)`，
   * 也就是「把整张精灵铺满 0 到 16 的区间，形状取其中对应的子矩形」，
   * 而不是「整张贴图铺满四边形」。这里逐面复刻同一套 UV 映射，
   * 保证和原版打印件的外观一致。
   */
  private[item] def drawShape(poseStack: PoseStack,
                              buffer: MultiBufferSource,
                              shape: PrintData.Shape,
                              packedLight: Int,
                              packedOverlay: Int): Unit = {
    if (shape == null) return

    val sprite = spriteFor(shape.texture)
    if (sprite == null) return

    val bounds = shape.bounds
    val vc = buffer.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS))

    // 原实现：贴图为空字符串时用 0.25 的透明度画一个占位白块。
    val alpha = if (Strings.isNullOrEmpty(shape.texture)) 64 else 255
    val tint = shape.tint.getOrElse(0xFFFFFF)
    val r = (tint >> 16) & 0xFF
    val g = (tint >> 8) & 0xFF
    val b = tint & 0xFF

    val u0 = sprite.getU0
    val u1 = sprite.getU1
    val v0 = sprite.getV0
    val v1 = sprite.getV1

    def u(t: Float): Float = u0 + t * (u1 - u0)

    def v(t: Float): Float = v0 + t * (v1 - v0)

    /** 原 `getInterpolatedV(16 - y * 16)`：V 轴翻转。 */
    def vf(y: Float): Float = v0 + (1f - y) * (v1 - v0)

    def vertex(x: Double, y: Double, z: Double, tu: Float, tv: Float, nx: Float, ny: Float, nz: Float): Unit = {
      vc.addVertex(poseStack.last(), x.toFloat, y.toFloat, z.toFloat)
        .setColor(r, g, b, alpha)
        .setUv(tu, tv)
        .setOverlay(packedOverlay)
        .setLight(packedLight)
        .setNormal(nx, ny, nz)
    }

    val minX = bounds.minX
    val minY = bounds.minY
    val minZ = bounds.minZ
    val maxX = bounds.maxX
    val maxY = bounds.maxY
    val maxZ = bounds.maxZ

    // 正面（+Z）。
    vertex(minX, minY, maxZ, u(minX), vf(minY), 0, 0, 1)
    vertex(maxX, minY, maxZ, u(maxX), vf(minY), 0, 0, 1)
    vertex(maxX, maxY, maxZ, u(maxX), vf(maxY), 0, 0, 1)
    vertex(minX, maxY, maxZ, u(minX), vf(maxY), 0, 0, 1)

    // 背面（-Z）。
    vertex(maxX, minY, minZ, u(maxX), vf(minY), 0, 0, -1)
    vertex(minX, minY, minZ, u(minX), vf(minY), 0, 0, -1)
    vertex(minX, maxY, minZ, u(minX), vf(maxY), 0, 0, -1)
    vertex(maxX, maxY, minZ, u(maxX), vf(maxY), 0, 0, -1)

    // 顶面（+Y）。
    vertex(maxX, maxY, maxZ, u(maxX), v(maxZ), 0, 1, 0)
    vertex(maxX, maxY, minZ, u(maxX), v(minZ), 0, 1, 0)
    vertex(minX, maxY, minZ, u(minX), v(minZ), 0, 1, 0)
    vertex(minX, maxY, maxZ, u(minX), v(maxZ), 0, 1, 0)

    // 底面（-Y）。
    vertex(minX, minY, maxZ, u(minX), v(maxZ), 0, -1, 0)
    vertex(minX, minY, minZ, u(minX), v(minZ), 0, -1, 0)
    vertex(maxX, minY, minZ, u(maxX), v(minZ), 0, -1, 0)
    vertex(maxX, minY, maxZ, u(maxX), v(maxZ), 0, -1, 0)

    // 左面（+X）。
    vertex(maxX, maxY, maxZ, u(maxZ), vf(maxY), 1, 0, 0)
    vertex(maxX, minY, maxZ, u(maxZ), vf(minY), 1, 0, 0)
    vertex(maxX, minY, minZ, u(minZ), vf(minY), 1, 0, 0)
    vertex(maxX, maxY, minZ, u(minZ), vf(maxY), 1, 0, 0)

    // 右面（-X）。
    vertex(minX, minY, maxZ, u(maxZ), vf(minY), -1, 0, 0)
    vertex(minX, maxY, maxZ, u(maxZ), vf(maxY), -1, 0, 0)
    vertex(minX, maxY, minZ, u(minZ), vf(maxY), -1, 0, 0)
    vertex(minX, minY, minZ, u(minZ), vf(minY), -1, 0, 0)
  }

  /**
   * 把打印件 NBT 里的贴图名解析成方块图集里的精灵。
   *
   * 1.7.10 用 `TextureMap#getTextureExtry(name)` 按「注册名」查图标；
   * 1.21.1 的精灵名就是资源路径，且方块贴图在 `block/` 目录下，
   * 因此这里按两种常见写法依次尝试，都查不到时退回图集的 missingno 精灵
   * （`TextureAtlas#getSprite` 对未知名字本来就会返回它）。
   */
  private[item] def spriteFor(name: String): TextureAtlasSprite = {
    if (Strings.isNullOrEmpty(name)) return null

    val namespace = name.indexOf(':') match {
      case i if i >= 0 => name.substring(0, i)
      case _ => Settings.resourceDomain
    }
    val path = name.indexOf(':') match {
      case i if i >= 0 => name.substring(i + 1)
      case _ => name
    }
    val lower = path.toLowerCase(java.util.Locale.ROOT)

    val candidates = Seq(
      ResourceLocation.fromNamespaceAndPath(namespace, lower),
      ResourceLocation.fromNamespaceAndPath(namespace, "block/" + lower)
    )

    var fallback: TextureAtlasSprite = null
    for (candidate <- candidates) {
      val sprite = RenderUtil.sprite(candidate)
      if (sprite != null && !isMissing(sprite)) return sprite
      if (sprite != null && fallback == null) fallback = sprite
    }
    fallback
  }

  private def isMissing(sprite: TextureAtlasSprite): Boolean =
    sprite.contents() == null || sprite.contents().name() == MissingTextureAtlasSprite.getLocation
}

/**
 * 自定义物品渲染器的公共基类。
 *
 * 基类需要 `BlockEntityRenderDispatcher` / `EntityModelSet` 来缓存原版的
 * 盾牌 / 三叉戟 / 头颅模型；本工程的自定义渲染器完全覆写 `renderByItem`，
 * 不会用到这两个字段，因此资源重载是空实现。
 */
private[item] abstract class OcItemRenderer
  extends BlockEntityWithoutLevelRenderer(
    Minecraft.getInstance.getBlockEntityRenderDispatcher,
    Minecraft.getInstance.getEntityModels) {

  override def onResourceManagerReload(resourceManager: ResourceManager): Unit = ()
}

/**
 * 一个物品的扩展点骨架：只覆写 `getCustomRenderer`，渲染器延迟创建
 * （注册事件发生在客户端 setup 期间，那时 `Minecraft` 实例不一定已经就绪）。
 */
private[item] abstract class OcItemExtensions extends IClientItemExtensions

/**
 * 软盘（与战利品盘）的物品渲染器。
 *
 * 1.7.10 里 `ItemRenderer` 对软盘做的是「先让原版物品渲染器画底图，
 * 再在左上角叠印文件系统标签」。1.21.1 的对应做法是：
 *  - 底图交给 `ItemRenderer#renderStatic`（软盘的模型是普通烘焙模型）；
 *  - 标签用 `Font#drawInBatch` 写进同一个 `MultiBufferSource`。
 *
 * ==降级说明==
 * 如果软盘的模型 JSON 被改成 `builtin/entity`，`renderStatic` 会再次回到本方法，
 * 重入标志会让内层调用直接返回（不会无限递归，也不会崩），此时只会画出标签。
 * 真要在那种情况下也画出底图，需要自己写出底图四边形，或改用
 * `ItemDecorator` 在 GUI 里叠加标签（资源侧 / 注册侧的后续工作）。
 */
private[item] final class FloppyRenderer extends OcItemRenderer {
  /** 防重入：`renderStatic` 有可能再次回到这里。 */
  private var rendering = false

  override def renderByItem(stack: ItemStack,
                            displayContext: ItemDisplayContext,
                            poseStack: PoseStack,
                            buffer: MultiBufferSource,
                            packedLight: Int,
                            packedOverlay: Int): Unit = {
    if (stack == null || stack.isEmpty) return
    if (rendering) return

    rendering = true
    try {
      val mc = Minecraft.getInstance

      // 底图。
      mc.getItemRenderer.renderStatic(
        stack, displayContext, packedLight, packedOverlay, poseStack, buffer, mc.level, 0)

      // 标签。
      val font = mc.font
      val text = ChatFormatting.stripFormatting(ItemRenderer.floppyLabel(stack)).take(8)
      if (text.nonEmpty && font != null) {
        poseStack.pushPose()
        // 原实现按 GUI 缩放决定字号与位置；1.21.1 的物品渲染拿不到 GUI 缩放，
        // 这里固定成「1 字体像素 = 1/16 方块」，并把 Y 轴翻过来（物品空间 Y 向上）。
        poseStack.translate(0.25, 0.5625, 0.51)
        poseStack.scale(1 / 16f, -1 / 16f, 1 / 16f)
        font.drawInBatch(text, 0f, 0f, 0xFF000000, false,
          poseStack.last().pose, buffer, Font.DisplayMode.NORMAL, 0, 0xF000E0)
        poseStack.popPose()
      }
    }
    finally {
      rendering = false
    }
  }
}

/**
 * 打印件的物品渲染器。
 *
 * 1.21.1 的物品坐标系里，自定义渲染器的原点是方块的最小角
 * （`ItemRenderer#render` 已经平移了 -0.5），所以形状的 0 到 1 坐标可以直接使用，
 * 不需要 1.7.10 里针对 ENTITY 上下文的那次 `glTranslatef(-0.5, 0, -0.5)`。
 */
private[item] final class PrintRenderer extends OcItemRenderer {
  override def renderByItem(stack: ItemStack,
                            displayContext: ItemDisplayContext,
                            poseStack: PoseStack,
                            buffer: MultiBufferSource,
                            packedLight: Int,
                            packedOverlay: Int): Unit = {
    if (stack == null || stack.isEmpty) return

    val data = new PrintData(stack)
    val shapes =
      if (data.hasActiveState && KeyBindings.showExtendedTooltips) data.stateOn.toSeq
      else data.stateOff.toSeq

    // 形状集合为空时画占位块（原 `drawShape(nullShape)`）。
    val toDraw = if (shapes.nonEmpty) shapes else Seq(ItemRenderer.nullShape)

    poseStack.pushPose()
    toDraw.foreach(shape => ItemRenderer.drawShape(poseStack, buffer, shape, packedLight, packedOverlay))
    poseStack.popPose()
  }
}

/**
 * 无人机的物品渲染器（原 `ItemRenderer` 里 `descriptor == drone` 的分支）。
 *
 * 1.7.10 会按 `ItemRenderType` 调整摆放（物品栏里放大 13 倍、装备时缩到 1.5 倍）；
 * 1.21.1 的摆放交给模型 JSON 的 `display` 段，这里只负责画出模型本体。
 */
private[item] final class DroneItemRenderer extends OcItemRenderer {
  private lazy val model: ModelQuadcopter =
    new ModelQuadcopter(Minecraft.getInstance.getEntityModels
      .bakeLayer(li.cil.oc.client.renderer.entity.DroneRenderer.Layer))

  override def renderByItem(stack: ItemStack,
                            displayContext: ItemDisplayContext,
                            poseStack: PoseStack,
                            buffer: MultiBufferSource,
                            packedLight: Int,
                            packedOverlay: Int): Unit = {
    if (stack == null || stack.isEmpty) return

    model.setupStaticAnim()

    poseStack.pushPose()
    // 模型是「实体坐标系」（Y 轴向下），物品坐标系 Y 轴向上，
    // 因此按原版实体渲染器的惯例翻转 X / Y。
    poseStack.translate(0.5, 0.5, 0.5)
    poseStack.scale(-0.9f, -0.9f, 0.9f)

    val bodyBuffer = buffer.getBuffer(RenderType.entityCutoutNoCull(ModelQuadcopter.Texture))
    model.renderToBuffer(poseStack, bodyBuffer, packedLight, packedOverlay, -1)

    val lightBuffer = buffer.getBuffer(RenderType.entityTranslucentEmissive(ModelQuadcopter.Texture))
    model.renderLights(poseStack, lightBuffer, ModelQuadcopter.FullBright, packedOverlay, 0xFF000000 | 0x66DD55)

    poseStack.popPose()
  }
}

/** 软盘（与战利品盘）的扩展点。 */
private[item] final class FloppyItemExtensions extends OcItemExtensions {
  private lazy val renderer: BlockEntityWithoutLevelRenderer = new FloppyRenderer
  override def getCustomRenderer(): BlockEntityWithoutLevelRenderer = renderer
}

/** 打印件的扩展点。 */
private[item] final class PrintItemExtensions extends OcItemExtensions {
  private lazy val renderer: BlockEntityWithoutLevelRenderer = new PrintRenderer
  override def getCustomRenderer(): BlockEntityWithoutLevelRenderer = renderer
}

/** 无人机的扩展点。 */
private[item] final class DroneItemExtensions extends OcItemExtensions {
  private lazy val renderer: BlockEntityWithoutLevelRenderer = new DroneItemRenderer
  override def getCustomRenderer(): BlockEntityWithoutLevelRenderer = renderer
}
