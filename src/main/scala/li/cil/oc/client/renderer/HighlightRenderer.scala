package li.cil.oc.client.renderer

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.tileentity.RenderUtil
import li.cil.oc.common
import li.cil.oc.util.ExtendedAABB._
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.{OverlayTexture, TextureAtlas, TextureAtlasSprite}
import net.minecraft.client.renderer.{LevelRenderer, RenderType}
import net.minecraft.core.Direction
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.{AABB, Vec3}
import net.neoforged.neoforge.client.event.RenderHighlightEvent
import net.neoforged.neoforge.common.NeoForge

import scala.util.Random

/**
 * 准星高亮覆盖层。
 *
 * ==1.7.10 状态==
 * 监听 `DrawBlockHighlightEvent`，做两件事：
 *  1. 玩家主手拿着平板（tablet）时，在准星指向方块的**命中面**上贴一层
 *     绿色半透明全息材质（`Textures.blockHologram`，alpha 0.4），并按
 *     `Settings.get.hologramFlickerFrequency` 的概率做一点抖动
 *     （缩放 + 平移的随机微扰）；
 *  2. 如果指向的是 `tileentity.Print`，则不画全息层，改为按打印件当前的
 *     形状列表（`print.data.stateOn` / `stateOff`，按 `print.facing` 旋转）
 *     用 `RenderGlobal.drawOutlinedBoundingBox` 描黑框，并取消原版高亮。
 *
 * ==1.21.1 迁移要点==
 *  - `DrawBlockHighlightEvent` → [[RenderHighlightEvent.Block]]
 *    （NeoForge 1.21.1，可取消；由 `LevelRenderer.renderLevel` 在画选区
 *    描边之前 post，见 `ClientHooks.onDrawHighlight`）。
 *  - 事件自带 `PoseStack` 与 `MultiBufferSource`，不再需要
 *    `GL11.glPushMatrix` / `Tessellator`。注意事件里的 `PoseStack` 已经以
 *    **相机位置**为原点（`LevelRenderer.renderLevel` 里取
 *    `camera.getPosition()` 作为 `d0/d1/d2`），所以绘制前要把方块坐标
 *    减掉相机坐标再 translate。
 *  - `e.player` → `Minecraft.getInstance.player`；`e.currentItem` →
 *    `player.getMainHandItem`。
 *  - `hitInfo.sideHit` → `BlockHitResult#getDirection`；
 *    `hitInfo.blockX/Y/Z` → `BlockHitResult#getBlockPos`。
 *  - `block.getSelectedBoundingBoxFromPool(pos)` → 方块形状的包围盒
 *    （`state.getShape(level, pos, CollisionContext)`），再减去方块坐标
 *    换算成 0..1 的相对包围盒。
 *  - `api.Items.get(e.currentItem) == tablet` → `api.Items.get(player.getMainHandItem)`。
 *  - 描边框：`RenderGlobal.drawOutlinedBoundingBox` →
 *    `LevelRenderer.renderLineBox(pose, consumer, AABB, r, g, b, a)` 配合
 *    `RenderType.lines()`（1.21.1 里 `RenderType.LINES` 常量已改为方法 `lines()`）。
 *  - `Settings.get.hologramFlickerFrequency` 仍然存在，语义不变。
 *  - 注册方式：**不用** `@SubscribeEvent` 注解扫描（原因见
 *    `common/event/EventHandlers` 的说明），改为在 [[initialize]] 里显式
 *    `NeoForge.EVENT_BUS.addListener`，由 `client/Proxy.clientSetup` 调用一次。
 */
object HighlightRenderer {
  private val random = new Random()

  /** 原 `lazy val tablet = api.Items.get(Constants.ItemName.Tablet)`。 */
  private lazy val tablet = api.Items.get(Constants.ItemName.Tablet)

  private var initialized = false

  /**
   * 注册运行期监听器；由 `client/Proxy.clientSetup` 调用一次。
   *
   * 签名固定为 `def initialize(): Unit`，不要改。
   */
  def initialize(): Unit = {
    if (initialized) return
    initialized = true
    NeoForge.EVENT_BUS.addListener((e: RenderHighlightEvent.Block) => onDrawBlockHighlight(e))
  }

  /**
   * 原 `onDrawBlockHighlight(e: DrawBlockHighlightEvent)`。
   *
   * 1.21.1 对应 [[RenderHighlightEvent.Block]]。
   */
  def onDrawBlockHighlight(e: RenderHighlightEvent.Block): Unit = {
    val mc = Minecraft.getInstance
    if (mc == null) return
    val player = mc.player
    if (player == null) return

    val pose = e.getPoseStack
    val buffer = e.getMultiBufferSource
    if (pose == null || buffer == null) return

    val level = player.level()
    if (level == null) return

    val hit = e.getTarget
    if (hit == null) return
    val pos = hit.getBlockPos
    val side = hit.getDirection
    val state = level.getBlockState(pos)

    // 事件 PoseStack 的原点就是相机位置。
    val cameraPos: Vec3 = e.getCamera.getPosition

    // --------------------------------------------------------------------- //
    // 1) 手持平板时的全息高亮覆盖层
    // --------------------------------------------------------------------- //
    val holdingTablet =
      tablet != null && api.Items.get(player.getMainHandItem) == tablet

    if (holdingTablet && !state.isAir) {
      val shape = state.getShape(level, pos, CollisionContext.of(player))
      if (!shape.isEmpty) {
        // 原 `getSelectedBoundingBoxFromPool` 返回世界坐标下的绝对包围盒，
        // 这里减掉方块坐标得到 0..1 的相对包围盒（等价于原实现的
        // `getOffsetBoundingBox(-x, -y, -z)`）。
        val bounds: AABB = shape.bounds()
          .move(-pos.getX.toDouble, -pos.getY.toDouble, -pos.getZ.toDouble)

        pose.pushPose()
        // 把原点移到方块位置（相机相对坐标系）。
        pose.translate(
          pos.getX.toDouble - cameraPos.x,
          pos.getY.toDouble - cameraPos.y,
          pos.getZ.toDouble - cameraPos.z)
        // 原 `GL11.glScaled(1.002, 1.002, 1.002)`：避免与方块本体 z-fighting。
        pose.scale(1.002f, 1.002f, 1.002f)

        if (Settings.get.hologramFlickerFrequency > 0 &&
          random.nextDouble() < Settings.get.hologramFlickerFrequency) {
          // 原实现：只在命中面所在的两个轴上抖动，避免从侧面看"穿帮"。
          val sx = 1 - math.abs(side.getStepX)
          val sy = 1 - math.abs(side.getStepY)
          val sz = 1 - math.abs(side.getStepZ)
          pose.scale(
            (1 + random.nextGaussian() * 0.01).toFloat,
            (1 + random.nextGaussian() * 0.001).toFloat,
            (1 + random.nextGaussian() * 0.01).toFloat)
          pose.translate(
            random.nextGaussian() * 0.01 * sx,
            random.nextGaussian() * 0.01 * sy,
            random.nextGaussian() * 0.01 * sz)
        }

        // 1.7.10 用 `Textures.blockHologram` + `OpenGlHelper.glBlendFunc(SRC_ALPHA, ONE)`
        // （加法混合）+ `GL11.glColor4f(0, 1, 0, 0.4)`。
        // 1.21.1 没有固定管线混合函数：改用「带方块图集的半透明渲染类型」+
        // 逐顶点颜色表达，贴图取 `Textures.Block.HologramEffect` 的精灵。
        val sprite = RenderUtil.sprite(Textures.Block.HologramEffect)
        val consumer = buffer.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS))
        drawFace(pose, consumer, bounds, side, sprite)
        pose.popPose()
      }
    }

    // --------------------------------------------------------------------- //
    // 2) 指向「打印件」时改画线框
    // --------------------------------------------------------------------- //
    level.getBlockEntity(pos) match {
      case print: common.tileentity.Print =>
        val shapes = if (print.state) print.data.stateOn else print.data.stateOff
        val consumer = buffer.getBuffer(RenderType.lines())
        pose.pushPose()
        pose.translate(
          pos.getX.toDouble - cameraPos.x,
          pos.getY.toDouble - cameraPos.y,
          pos.getZ.toDouble - cameraPos.z)
        val expansion = 0.002f
        for (shape <- shapes) {
          // 1.21.1 的 `AABB` 不可变，`rotateTowards` / `inflate` 都返回新实例。
          val box = shape.bounds.rotateTowards(print.facing).inflate(expansion, expansion, expansion)
          // 原 `RenderGlobal.drawOutlinedBoundingBox(bounds, -1)`：颜色 -1 即黑框，
          // 原代码用的是 `GL11.glColor4f(0, 0, 0, 0.4f)`。
          LevelRenderer.renderLineBox(pose, consumer, box, 0f, 0f, 0f, 0.4f)
        }
        pose.popPose()

        // 原 `e.setCanceled(true)`：抑制原版高亮描边。
        e.setCanceled(true)
      case _ =>
        // 不是打印件：保留原版高亮。
    }
  }

  /**
   * 在命中面上画一个四边形。
   *
   * 1.7.10 用 `Tessellator.instance.addVertexWithUV(...)` 加手算 UV
   * （`bounds.maxZ * 16` 这种，即按 16 像素贴图算坐标）；1.21.1 改成
   * `VertexConsumer` + 图集精灵的 `getU0/getV0/getU1/getV1`。
   *
   * 四个角的顺序逐个直译原实现的六个 `sideHit match` 分支。
   */
  private def drawFace(pose: PoseStack,
                       consumer: VertexConsumer,
                       bounds: AABB,
                       side: Direction,
                       sprite: TextureAtlasSprite): Unit = {
    if (sprite == null) return
    val u0 = sprite.getU0
    val v0 = sprite.getV0
    val u1 = sprite.getU1
    val v1 = sprite.getV1

    // 原实现的颜色：`GL11.glColor4f(0.0F, 1.0F, 0.0F, 0.4F)`。
    val r = 0f
    val g = 1f
    val b = 0f
    val a = 0.4f

    val e = 0.002
    val corners: Array[(Double, Double, Double)] = side match {
      case Direction.UP =>
        Array((bounds.maxX, bounds.maxY + e, bounds.maxZ),
          (bounds.maxX, bounds.maxY + e, bounds.minZ),
          (bounds.minX, bounds.maxY + e, bounds.minZ),
          (bounds.minX, bounds.maxY + e, bounds.maxZ))
      case Direction.DOWN =>
        Array((bounds.maxX, bounds.minY - e, bounds.minZ),
          (bounds.maxX, bounds.minY - e, bounds.maxZ),
          (bounds.minX, bounds.minY - e, bounds.maxZ),
          (bounds.minX, bounds.minY - e, bounds.minZ))
      case Direction.EAST =>
        Array((bounds.maxX + e, bounds.maxY, bounds.minZ),
          (bounds.maxX + e, bounds.maxY, bounds.maxZ),
          (bounds.maxX + e, bounds.minY, bounds.maxZ),
          (bounds.maxX + e, bounds.minY, bounds.minZ))
      case Direction.WEST =>
        Array((bounds.minX - e, bounds.maxY, bounds.maxZ),
          (bounds.minX - e, bounds.maxY, bounds.minZ),
          (bounds.minX - e, bounds.minY, bounds.minZ),
          (bounds.minX - e, bounds.minY, bounds.maxZ))
      case Direction.SOUTH =>
        Array((bounds.maxX, bounds.maxY, bounds.maxZ + e),
          (bounds.minX, bounds.maxY, bounds.maxZ + e),
          (bounds.minX, bounds.minY, bounds.maxZ + e),
          (bounds.maxX, bounds.minY, bounds.maxZ + e))
      case _ =>
        Array((bounds.minX, bounds.maxY, bounds.minZ - e),
          (bounds.maxX, bounds.maxY, bounds.minZ - e),
          (bounds.maxX, bounds.minY, bounds.minZ - e),
          (bounds.minX, bounds.minY, bounds.minZ - e))
    }

    val poseEntry = pose.last()
    val nx = side.getStepX.toFloat
    val ny = side.getStepY.toFloat
    val nz = side.getStepZ.toFloat
    // UV 对应关系：第一个角在「右下」，第三个角在「左上」。
    val uvs = Array((u1, v1), (u1, v0), (u0, v0), (u0, v1))
    var i = 0
    while (i < 4) {
      val (x, y, z) = corners(i)
      val (uu, vv) = uvs(i)
      consumer.addVertex(poseEntry, x.toFloat, y.toFloat, z.toFloat)
        .setColor(r, g, b, a)
        .setUv(uu, vv)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(RenderUtil.fullBright)
        .setNormal(poseEntry, nx, ny, nz)
      i += 1
    }
  }
}
