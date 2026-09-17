package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.common.tileentity.Screen
import li.cil.oc.util.{Color, RenderState}
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer, BlockEntityRendererProvider}
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{LevelRenderer, MultiBufferSource, RenderType}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB

/**
 * 屏幕（Screen）方块实体渲染器。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - `object ScreenRenderer extends TileEntitySpecialRenderer` 改成
 *    `class ... extends BlockEntityRenderer[Screen]`；渲染器由
 *    `EntityRenderersEvent.RegisterRenderers` 按 `BlockEntityType` 注册
 *    （见 `client/Proxy.registerRenderers`）。
 *  - `glPushMatrix` / `glTranslated(x + 0.5, ...)`：1.21.1 的 `PoseStack`
 *    入场原点已经是方块角，因此只 `translate(0.5, 0.5, 0.5)`。
 *  - `GL11.glRotatef/glScalef/glTranslatef` → `PoseStack#mulPose` / `scale` / `translate`。
 *  - `Tessellator` + `bindTexture` + `addVertexWithUV` → `RenderUtil.drawSpriteQuad`
 *    （方块图集精灵 + `VertexConsumer`）。
 *  - `bindTexture` / `GL11.glDepthMask` / `glPushAttrib` 全部删除：1.21.1 的深度写开关
 *    由 [MultiBufferSource] 的 [RenderType] 决定。
 *  - `GLContext.getCapabilities.OpenGL14`（能不能用 `glBlendColor` 做距离淡出）
 *    整体删除：1.21.1 的渲染管线统一由着色器管理混合，不存在「OpenGL 1.4 才支持」的分支。
 *    淡出改为不作为（原版在 1.21.1 里也不再支持常量 alpha 混合的自定义颜色）。
 *  - `Minecraft.getMinecraft.mcProfiler` → `Minecraft.getInstance.getProfiler`。
 *  - `screen.getRenderBoundingBox`（1.7.10 的 `TileEntity`）→ 由
 *    [getRenderBoundingBox] 提供；多方块屏幕的包围盒按 `width` / `height` 展开。
 */
class ScreenRenderer(context: BlockEntityRendererProvider.Context) extends BlockEntityRenderer[Screen] {
  private val maxRenderDistanceSq =
    Settings.get.maxScreenTextRenderDistance * Settings.get.maxScreenTextRenderDistance

  private val fadeDistanceSq =
    Settings.get.screenTextFadeStartDistance * Settings.get.screenTextFadeStartDistance

  /** 原 `lazy val screens`：手里拿着任意等级的屏幕时也显示「上方向」指示。 */
  private lazy val screens = Set(
    api.Items.get(Constants.BlockName.ScreenTier1),
    api.Items.get(Constants.BlockName.ScreenTier2),
    api.Items.get(Constants.BlockName.ScreenTier3))

  /** 无参构造重载：`EntityRenderersEvent` 里的工厂目前这样调用。 */
  def this() = this(null)

  /**
   * 多方块屏幕的渲染包围盒：以原点方块为中心，向右 / 向上展开 `width` × `height`。
   *
   * 1.21.1 的 `BlockEntityRenderer#shouldRender` 只按单个方块位置 + 这个包围盒裁剪，
   * 因此多方块屏幕必须覆写它，否则屏幕超出部分会被整体裁掉。
   */
  override def getRenderBoundingBox(screen: Screen): AABB = {
    if (screen == null) return AABB.INFINITE
    val pos = screen.getBlockPos
    val right = screen.toGlobal(Direction.EAST)
    val up = screen.toGlobal(Direction.UP)
    // 只按「右 / 上」两个轴展开；厚度方向只多留一格，够贴住表面即可。
    val pos2 = new BlockPos(
      pos.getX + (if (right.getStepX != 0) right.getStepX * (screen.width - 1) else 0),
      pos.getY + (if (up.getStepY != 0) up.getStepY * (screen.height - 1) else 0),
      pos.getZ + (if (right.getStepZ != 0) right.getStepZ * (screen.width - 1) else 0))
    val x0 = math.min(pos.getX, pos2.getX) - 1
    val y0 = math.min(pos.getY, pos2.getY) - 1
    val z0 = math.min(pos.getZ, pos2.getZ) - 1
    val x1 = math.max(pos.getX, pos2.getX) + 2
    val y1 = math.max(pos.getY, pos2.getY) + 2
    val z1 = math.max(pos.getZ, pos2.getZ) + 2
    new AABB(x0, y0, z0, x1, y1, z1)
  }

  // ----------------------------------------------------------------------- //
  // 渲染
  // ----------------------------------------------------------------------- //

  override def render(screen: Screen, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (screen == null) return
    RenderState.checkError(getClass.getName + ".render: entering (aka: wasntme)")

    // 只有多方块屏幕的原点负责绘制，其余方块由包围盒覆盖。
    if (!screen.isOrigin) return

    // 屏幕的边框贴图覆盖层：它同时承担「拼接」（按 width/height/localPosition 换贴图）
    // 与「朝向」（按 pitch/yaw 决定每个世界面贴哪张图）两件事，属于**方块外观**，
    // 因此不受下面的文本渲染距离与朝向剔除影响 —— 否则从背面看或离远了看，
    // 屏幕又会退化成静态模型的单块贴图，拼接和朝向都时有时无。
    // 必须在这里（还没 push + translate(0.5, ...)）画：覆盖层是按每个方块的
    // 局部坐标写顶点的，此刻 pose 的入场原点正好是本方块角。
    //
    // ==为什么单方块屏幕也要走这一层（而不是只交给静态模型）==
    // 1.20 CE 的屏幕模型（`models/block/screen.json` → `generic_top` / `generic_side`）
    // 之所以不需要渲染器补贴图，是因为它的 `blockstates/screen1.json` 写了 **12 个
    // `pitch` / `yaw` 变体**，靠方块状态本身把模型转到位；本项目移植时
    // `blockstates/screen1.json` 只有一个 `""` 变体（无 pitch / yaw 属性），静态模型
    // 永远把 `screen/f2` 画在朝南那一面。也就是说：**只要屏幕不是「放在地上、正面朝南」，
    // 静态模型的贴图朝向就是错的**，而 blockstate 属于本次不可改动的范围，
    // 补朝向这件事只能由这个渲染器来做。因此覆盖层对所有屏幕（含单方块）生效。
    //
    // ==同时必须写等级染色==
    // 1.7.10 里这些图标是普通方块图标，天然经过 `BlockScreen#getRenderColor`
    // （`Color.byTier(tier)`，见 [li.cil.oc.util.Color]）染色；1.21.1 的静态模型同样
    // 继承 `tinted_cube`（六面 `tintindex: 0`），由 [li.cil.oc.client.ColorHandlers]
    // 提供同一个颜色。而 [li.cil.oc.client.renderer.tileentity.RenderUtil.drawSpriteQuad]
    // 把顶点色写死成白色，会把这份染色整个抹掉 —— 详见 [drawFace] 的说明。
    drawScreenOverlay(screen, pose, buffer, overlay)

    val distance = playerDistanceSq(screen) / math.min(screen.width, screen.height)
    if (distance > maxRenderDistanceSq) return

    // 粗略判断本机玩家是否能看到屏幕正面：屏幕朝向的反方向与「相机 -> 屏幕」向量的夹角。
    // 1.7.10 里渲染回调拿到的 x/y/z 是「方块坐标 - 相机坐标」（指向屏幕），条件写作
    //   `facing.getOpposite · (方块 - 玩家) < 0 → 不画`，也就是「玩家在屏幕背面时剔除」。
    // 而原移植版把向量写成了 `玩家 - 方块`，符号正好反了 —— 结果玩家站在**正面**时
    // 反而被剔除，屏幕上的内容永远看不到（实机表现就是「屏幕拼不起来 / 一直空着」）。
    val player = Minecraft.getInstance.player
    if (player == null) return
    val screenFacing = screen.facing.getOpposite
    val px = screen.getBlockPos.getX + 0.5 - player.getX
    val py = screen.getBlockPos.getY + 0.5 - player.getY
    val pz = screen.getBlockPos.getZ + 0.5 - player.getZ
    if (screenFacing.getStepX * px + screenFacing.getStepY * py + screenFacing.getStepZ * pz < 0) return

    RenderState.checkError(getClass.getName + ".render: checks")

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    drawOverlay(screen, pose, buffer, overlay)

    RenderState.checkError(getClass.getName + ".render: overlay")

    // TODO(渲染): 原实现在距离超过 `screenTextFadeStartDistance` 时用
    // `GL14.glBlendColor` + `GL_CONSTANT_ALPHA` 做淡出。1.21.1 的渲染管线没有
    // 等价的「常量 alpha 混合」，这里保持全不透明（不再有渐变淡出）。

    if (screen.buffer != null && screen.buffer.isRenderingEnabled()) {
      val profiler = Minecraft.getInstance.getProfiler
      profiler.push("opencomputers_neo:screen_text")
      draw(screen, pose, buffer)
      profiler.pop()
    }

    pose.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }

  /**
   * 把当前姿态变换到「屏幕内容平面」：先按 `yaw` / `pitch` 转正，再把原点
   * 移到屏幕左下角，最后翻转 Y 轴（缓冲区第 0 行在顶部）。
   *
   * 与原实现（一串 `GL11.glRotatef` / `glTranslatef`）逐条对应。
   */
  private def transform(screen: Screen, pose: PoseStack): Unit = {
    screen.yaw match {
      case Direction.WEST => pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180))
      case Direction.EAST => pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90))
      case _ => // 南向即默认朝向，不需要旋转。
    }
    screen.pitch match {
      case Direction.DOWN => pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90))
      case Direction.UP => pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90))
      case _ => // 水平屏幕没有俯仰。
    }

    // 把区域对齐到屏幕（左下角对左下角）。
    pose.translate(-0.5f, -0.5f, 0.5f)
    pose.translate(0f, screen.height.toFloat, 0f)

    // 把文本上下翻转。
    pose.scale(1f, -1f, 1f)
  }

  /**
   * 「上方向」指示贴图：玩家手里拿着任意等级的屏幕（或适用的扳手）、且屏幕朝向
   * 天 / 地时显示，用来提示屏幕拼接的方向。
   *
   * 原实现用 `Tessellator` 画 1x1 的贴图四边形；这里走
   * [RenderUtil.drawSpriteQuad]（方块图集精灵，自发光 + 无背面剔除）。
   */
  private def drawOverlay(screen: Screen, pose: PoseStack, buffer: MultiBufferSource, overlay: Int): Unit = {
    if (screen.facing != Direction.UP && screen.facing != Direction.DOWN) return

    val player = Minecraft.getInstance.player
    if (player == null) return

    val stack = player.getMainHandItem
    if (stack == null || stack.isEmpty) return

    // TODO(渲染): 原实现还接受「手里拿着适用的扳手」这一条件
    // （`integration.util.Wrench.holdsApplicableWrench`）。`li.cil.oc.integration`
    // 目前不在编译集里，因此这里只保留「手里拿着任意等级的屏幕」这一半条件。
    // 等 `integration` 进编译集后，把扳手判定加回来即可。
    val applicable = screens.contains(api.Items.get(stack))
    if (!applicable) return

    val sprite = RenderUtil.sprite(Textures.Block.ScreenUpIndicator)
    if (sprite == null) return

    pose.pushPose()
    transform(screen, pose)
    // 原实现：`glTranslatef(width / 2 - 0.5, height / 2 - 0.5, 0.05)`。
    pose.translate(screen.width / 2f - 0.5f, screen.height / 2f - 0.5f, 0.05f)
    val vc = buffer.getBuffer(RenderType.cutout())
    RenderUtil.drawSpriteQuad(pose, vc, sprite,
      0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0,
      RenderUtil.fullBright, overlay)
    pose.popPose()
  }

  /**
   * 画出文本缓冲区的内容。
   *
   * 坐标变换与原实现一致：先 [transform]，再从边距偏移、按缓冲区与屏幕的尺寸比缩放，
   * 最后沿 Z 轴抬 0.01 避免与屏幕本体 z-fighting。
   */
  private def draw(screen: Screen, pose: PoseStack, buffer: MultiBufferSource): Unit = {
    RenderState.checkError(getClass.getName + ".draw: entering (aka: wasntme)")

    val sx = screen.width
    val sy = screen.height
    val tw = sx * 16f
    val th = sy * 16f

    transform(screen, pose)

    // 从边框偏移。
    pose.translate(sx * 2.25f / tw, sy * 2.25f / th, 0f)

    // 内尺寸（减去边框）。
    val isx = sx - (4.5f / 16)
    val isy = sy - (4.5f / 16)

    // 按实际缓冲尺寸缩放（保持长宽比，居中）。
    // `api.internal.TextBuffer` 是 Java 接口，这里是方法调用（不是字段访问）。
    val sizeX = screen.buffer.renderWidth()
    val sizeY = screen.buffer.renderHeight()
    val scaleX = isx / sizeX
    val scaleY = isy / sizeY
    if (scaleX > scaleY) {
      pose.translate(sizeX * 0.5f * (scaleX - scaleY), 0f, 0f)
      pose.scale(scaleY, scaleY, 1f)
    }
    else {
      pose.translate(0f, sizeY * 0.5f * (scaleY - scaleX), 0f)
      pose.scale(scaleX, scaleX, 1f)
    }

    // 稍微抬一点，避免文字切进屏幕表面。
    pose.translate(0d, 0d, 0.01d)

    RenderState.checkError(getClass.getName + ".draw: setup")

    // 渲染实际文本（见 [BufferRenderer.drawText] 的降级说明）。
    BufferRenderer.drawText(pose, buffer, screen.buffer)

    RenderState.checkError(getClass.getName + ".draw: text")
  }

  /**
   * 玩家到屏幕包围盒的平方距离（点到 AABB 的最短距离）。
   *
   * 1.7.10 用 `screen.getRenderBoundingBox`（方块实体自带）；1.21.1 的
   * `BlockEntity` 没有该成员，这里按世界坐标重建同一个盒子。
   */
  private def playerDistanceSq(screen: Screen): Double = {
    val player = Minecraft.getInstance.player
    if (player == null) return Double.MaxValue

    val pos = screen.getBlockPos
    val minX = pos.getX.toDouble
    val minY = pos.getY.toDouble
    val minZ = pos.getZ.toDouble
    val ex = math.max(1, screen.width).toDouble
    val ey = math.max(1, screen.height).toDouble
    val ez = 1.0
    val maxX = minX + ex
    val maxY = minY + ey
    val maxZ = minZ + ez

    val cx = (minX + maxX) * 0.5
    val cy = (minY + maxY) * 0.5
    val cz = (minZ + maxZ) * 0.5
    val dx = player.getX - cx
    val dy = player.getY - cy
    val dz = player.getZ - cz

    // 与原实现相同的「按半尺寸外扩」的写法：把超出的部分计入距离。
    clampAxis(dx, ex * 0.5) + clampAxis(dy, ey * 0.5) + clampAxis(dz, ez * 0.5)
  }

  /** 单轴分量：在盒内时为 0，超出时取超出量（按盒子外接尺寸缩放后的）平方。 */
  private def clampAxis(d: Double, half: Double): Double = {
    if (d < -half) {
      val e = d + half
      e * e
    }
    else if (d > half) {
      val e = d - half
      e * e
    }
    else 0
  }

  // ----------------------------------------------------------------------- //
  // 多方块屏幕的边框贴图
  // ----------------------------------------------------------------------- //

  /**
   * 多方块屏幕的贴图位置表，与 1.7.10 `common.block.Screen.Icons` 一一对应
   * （都在 `assets/opencomputers_neo/textures/block/screen/` 下）。
   *
   * 命名规则：
   *  - 首字母 `f` / `b`：正面（front）/ 背面（back）；
   *  - 第二字母 `h` / `v`：水平边 / 垂直边；`t` / `m` / `b`：上 / 中 / 下；
   *    `l` / `m` / `r`：左 / 中 / 右；
   *  - 结尾 `t` / `m` / `b` 与 `l` / `m` / `r` 同上；后缀 `2` 表示「单方块 / 无环境光遮蔽」的那一份。
   *
   * 1.7.10 里这些是 `IIcon`，由 `TextureStitchEvent` 从 `textures/blocks/screen/` 注册并缓存；
   * 1.21.1 没有贴图注册事件，所以这里只保存**位置**，绘制时再用 [RenderUtil.sprite] 取精灵。
   */
  private object ScreenIcons {
    val b = screenTex("b")
    val b2 = screenTex("b2")
    val bbl = screenTex("bbl")
    val bbl2 = screenTex("bbl2")
    val bbm = screenTex("bbm")
    val bbm2 = screenTex("bbm2")
    val bbr = screenTex("bbr")
    val bbr2 = screenTex("bbr2")
    val bhb = screenTex("bhb")
    val bhb2 = screenTex("bhb2")
    val bhm = screenTex("bhm")
    val bhm2 = screenTex("bhm2")
    val bht = screenTex("bht")
    val bht2 = screenTex("bht2")
    val bml = screenTex("bml")
    val bmm = screenTex("bmm")
    val bmr = screenTex("bmr")
    val btl = screenTex("btl")
    val btm = screenTex("btm")
    val btr = screenTex("btr")
    val bvb = screenTex("bvb")
    val bvb2 = screenTex("bvb2")
    val bvm = screenTex("bvm")
    val bvt = screenTex("bvt")
    val f = screenTex("f")
    val f2 = screenTex("f2")
    val fbl = screenTex("fbl")
    val fbl2 = screenTex("fbl2")
    val fbm = screenTex("fbm")
    val fbm2 = screenTex("fbm2")
    val fbr = screenTex("fbr")
    val fbr2 = screenTex("fbr2")
    val fhb = screenTex("fhb")
    val fhb2 = screenTex("fhb2")
    val fhm = screenTex("fhm")
    val fhm2 = screenTex("fhm2")
    val fht = screenTex("fht")
    val fht2 = screenTex("fht2")
    val fml = screenTex("fml")
    val fmm = screenTex("fmm")
    val fmr = screenTex("fmr")
    val ftl = screenTex("ftl")
    val ftm = screenTex("ftm")
    val ftr = screenTex("ftr")
    val fvb = screenTex("fvb")
    val fvb2 = screenTex("fvb2")
    val fvm = screenTex("fvm")
    val fvt = screenTex("fvt")

    // 下面这些「阵列」的排法与 1.7.10 完全相同：把上面 15 张贴图按
    // 「上边 / 左边 / 角 / 中间」等语义排成 15 元数组，供 [multiBlockIcon] 按下标取值。
    // 注意 1.7.10 原文里 `fv2 = Array(fvt, fvm, fvb2)`（第二项不是 `fvm2`）与
    // `sud = Array(bvt, bvm, bvb2)`，属于原实现的既有写法，这里照抄以保持一致。
    val fh = Array(fht, fhm, fhb)
    val fv = Array(fvt, fvm, fvb)
    val bh = Array(bht, bhm, bhb)
    val bv = Array(bvt, bvm, bvb)
    val fth = Array(ftl, ftm, ftr)
    val fmh = Array(fml, fmm, fmr)
    val fbh = Array(fbl, fbm, fbr)
    val bth = Array(btl, btm, btr)
    val bmh = Array(bml, bmm, bmr)
    val bbh = Array(bbl, bbm, bbr)
    val ftv = Array(ftl, fml, fbl)
    val fmv = Array(ftm, fmm, fbm)
    val fbv = Array(ftr, fmr, fbr)
    val btv = Array(btl, bml, bbl)
    val bmv = Array(btm, bmm, bbm)
    val bbv = Array(btr, bmr, bbr)
    val fh2 = Array(fht2, fhm2, fhb2)
    val fv2 = Array(fvt, fvm, fvb2)
    val bh2 = Array(bht2, bhm2, bhb2)
    val bv2 = Array(bvt, bvm, bvb2)
    val fbh2 = Array(fbl2, fbm2, fbr2)
    val bbh2 = Array(bbl2, bbm2, bbr2)

    val fud = fh2 ++ fv2 ++ fth ++ fmh ++ fbh2
    val bud = bh2.reverse ++ bv2 ++ bth.reverse ++ bmh.reverse ++ bbh2.reverse
    val fsn = fh ++ fv ++ fth ++ fmh ++ fbh
    val few = fv ++ fh ++ ftv ++ fmv ++ fbv
    val bsn = bh ++ bv ++ bth ++ bmh ++ bbh
    val bew = bv ++ bh ++ btv ++ bmv ++ bbv

    val sud = Array(bvt, bvm, bvb2)
    val sse = Array(bhb2, bhm2, bht2)
    val snw = Array(bht2, bhm2, bhb2)
    val th = Array(bhb, bhm, bht)
    val tv = Array(bvb, bvm, bvt)
  }

  /** 取 `textures/block/screen/` 下某张贴图在图集里的精灵位置。 */
  private def screenTex(name: String): ResourceLocation = RenderUtil.blockTexture("screen/" + name)

  /** 边框贴图相对方块表面的外扩量，用来压过方块模型避免 z-fighting。 */
  private val overlayEpsilon = 0.002

  /**
   * 逐行复刻 1.7.10 `common.block.Screen#getIcon` 的**多方块分支**：
   * 按方块在整屏里的位置（[Screen#localPosition]）与该面的局部朝向挑一张贴图。
   *
   * 原实现在 `yaw` 取值非法时会 `throw new AssertionError`；1.21.1 的 `Direction`
   * 只可能是 6 个合法方向（没有 `UNKNOWN`），因此那些分支退化为「不做换算」。
   */
  private def multiBlockIcon(screen: Screen, localSide: Direction): ResourceLocation = {
    import ScreenIcons._
    val right = screen.width - 1
    val bottom = screen.height - 1
    val (px, py) = screen.localPosition

    // 把「屏幕局部坐标」换算成「贴图坐标」：俯仰 / 偏航不同，贴图阵列的方向也不同。
    val (lx, ly) = screen.pitch match {
      case Direction.NORTH => (px, py)
      case Direction.UP => screen.yaw match {
        case Direction.SOUTH => (px, py)
        case Direction.NORTH => (right - px, bottom - py)
        case Direction.EAST => (right - px, py)
        case Direction.WEST => (px, bottom - py)
        case _ => (px, py)
      }
      case Direction.DOWN => screen.yaw match {
        case Direction.SOUTH => (px, bottom - py)
        case Direction.NORTH => (right - px, py)
        case Direction.EAST => (right - px, bottom - py)
        case Direction.WEST => (px, py)
        case _ => (px, py)
      }
      case _ => (px, py)
    }

    // 正面与背面、左右与上下可以用同一套下标规则，只是贴图集合不同。
    localSide match {
      case Direction.SOUTH | Direction.NORTH =>
        val (ud, sn, ew) =
          if (localSide == Direction.SOUTH) (fud, fsn, few) else (bud, bsn, bew)
        val Array(ht, hm, hb, vt, vm, vb, tl, tm, tr, ml, mm, mr, bl, bm, br) = screen.pitch match {
          case Direction.NORTH => ud
          case _ => screen.yaw match {
            case Direction.SOUTH | Direction.NORTH => sn
            case Direction.EAST | Direction.WEST => ew
            case _ => sn
          }
        }
        if (screen.height == 1) {
          if (lx == 0) ht
          else if (lx == right) hb
          else hm
        }
        else if (screen.width == 1) {
          if (ly == 0) vb
          else if (ly == bottom) vt
          else vm
        }
        else {
          if (lx == 0) {
            if (ly == 0) bl
            else if (ly == bottom) tl
            else ml
          }
          else if (lx == right) {
            if (ly == 0) br
            else if (ly == bottom) tr
            else mr
          }
          else {
            if (ly == 0) bm
            else if (ly == bottom) tm
            else mm
          }
        }

      case Direction.EAST | Direction.WEST =>
        val (ud, sn, ew) =
          if (localSide == Direction.EAST) (sud, sse, snw) else (sud, snw, sse)
        val Array(t, m, b) = screen.pitch match {
          case Direction.NORTH => ud
          case _ => screen.yaw match {
            case Direction.SOUTH | Direction.EAST => sn
            case Direction.NORTH | Direction.WEST => ew
            case _ => sn
          }
        }
        if (screen.height == 1) b2
        else if (ly == 0) b
        else if (ly == bottom) t
        else m

      case Direction.UP | Direction.DOWN =>
        val (sn, ew) =
          if ((localSide == Direction.UP) ^ (screen.pitch == Direction.DOWN)) (snw, sse) else (sse, snw)
        val Array(t, m, b) = screen.pitch match {
          case Direction.NORTH => screen.yaw match {
            case Direction.SOUTH => th
            case Direction.NORTH => bh
            case Direction.EAST => bv
            case Direction.WEST => tv
            case _ => th
          }
          case _ => screen.yaw match {
            case Direction.SOUTH | Direction.WEST => sn
            case Direction.NORTH | Direction.EAST => ew
            case _ => sn
          }
        }
        if (screen.width == 1) {
          if (screen.pitch == Direction.NORTH) b else b2
        }
        else if (lx == 0) b
        else if (lx == right) t
        else m

      case _ => b2
    }
  }

  /**
   * 给屏幕的每个方块、每个朝外的面铺上按「拼接位置 + 朝向」选出的贴图。
   *
   * ==为什么必须由渲染器补这一层（两个问题一起解决）==
   * 1.7.10 的屏幕外观是**完全数据驱动**的：`BlockScreen#getIcon(world, x, y, z, worldSide, localSide)`
   * 读取方块实体的 `width` / `height` / `localPosition` / `pitch` / `yaw`，
   * 再决定「这一个世界面该贴哪张图」。也就是说：
   *  - 相邻屏幕拼成大屏时，边框贴图会按位置换成「左中 / 上中 / 角 / 中间」；
   *  - 屏幕转个方向后，贴图会跟着转到新的世界面上。
   *
   * 1.21.1 的方块外观是**烘焙模型**（`models/block/screen1.json` 等），blockstate 里既没有
   * 「拼接位置」也没有「朝向」属性，静态模型永远只把单块屏幕的贴图画在朝南那一面。于是：
   *  - 几块屏幕挨在一起，看上去仍是各自独立的小屏（用户反馈的「拼不起来」）；
   *  - 屏幕转方向后外观纹丝不动（用户反馈的「固定朝向」）。
   *
   * 这里用方块实体渲染器把 1.7.10 的 `getIcon` 规则重放一遍：遍历多方块里的每个方块，
   * 对每个世界面用 `toLocal` 求出**局部面**，据此选出贴图，再贴在**那个世界面**上
   * （相对方块表面外扩 [overlayEpsilon] 以压过静态模型）。因为六个面都会被覆盖，
   * 静态模型朝南的那份贴图不会露出来，效果上等同于原来的动态图标。
   *
   * 单方块屏幕同样走这条路径（[singleBlockIcon]）：水平且未旋转时选出的贴图与静态模型
   * 完全一致，外观不变；拼接或换朝向后才跟着变。
   */
  private def drawScreenOverlay(screen: Screen, pose: PoseStack, buffer: MultiBufferSource, overlay: Int): Unit = {
    val level = screen.getLevel
    if (level == null) return

    // 同一个多方块里的所有方块位置：用来跳过屏幕内部的接缝面。
    val members = screen.screens
    val positions = members.map(_.getBlockPos).toSet
    val originPos = screen.getBlockPos

    // 整块大屏共用一个顶点消费者：`cutout` 与原版方块模型的切割阶段一致
    // （这些贴图带二值 alpha），深度测试会自然处理与模型本体的前后关系。
    val vc = buffer.getBuffer(RenderType.cutout())

    // 整个多方块同属一个等级，染色值取一次即可（见 [screenTint]）。
    val tint = screenTint(screen)

    for (member <- members) {
      val pos = member.getBlockPos
      val dx = pos.getX - originPos.getX
      val dy = pos.getY - originPos.getY
      val dz = pos.getZ - originPos.getZ
      val light = LevelRenderer.getLightColor(level, pos)
      for (worldSide <- Direction.values()) {
        // 该方向上的邻居也属于本多方块时，这个面在屏幕内部，不需要画。
        if (!positions.contains(pos.relative(worldSide))) {
          val sprite = RenderUtil.sprite(iconFor(member, member.toLocal(worldSide)))
          if (sprite != null) {
            pose.pushPose()
            pose.translate(dx.toDouble, dy.toDouble, dz.toDouble)
            drawFace(pose, vc, worldSide, sprite, tint, light, overlay)
            pose.popPose()
          }
        }
      }
    }
  }

  /** 单方块屏幕与多方块屏幕的贴图选择入口（对应 1.7.10 `getIcon` 的两个分支）。 */
  private def iconFor(screen: Screen, localSide: Direction): ResourceLocation =
    if (screen.width > 1 || screen.height > 1) multiBlockIcon(screen, localSide)
    else singleBlockIcon(screen, localSide)

  /**
   * 1.7.10 `common.block.Screen#getIcon` 的**单方块分支**（原 `case screen: tileentity.Screen`）：
   * 水平屏幕用 `f2` / `b2` / `b` / `b2`（无环境光遮蔽的那一份），
   * 俯仰为天 / 地时用 `f` / `b` / `b2` / `b2`。
   */
  private def singleBlockIcon(screen: Screen, localSide: Direction): ResourceLocation = {
    // 注意：这里的局部变量不能叫 `f` / `b` / `t` / `s` —— 那会和 `ScreenIcons` 里的
    // 同名字段撞上，Scala 会把右侧的 `b` 解析成正在定义的这个变量（递归值）而报错。
    val (frontIcon, backIcon, topIcon, otherIcon) = screen.pitch match {
      case Direction.NORTH => (ScreenIcons.f2, ScreenIcons.b2, ScreenIcons.b, ScreenIcons.b2)
      case _ => (ScreenIcons.f, ScreenIcons.b, ScreenIcons.b2, ScreenIcons.b2)
    }
    localSide match {
      case Direction.SOUTH => frontIcon
      case Direction.NORTH => backIcon
      case Direction.DOWN | Direction.UP => topIcon
      case _ => otherIcon
    }
  }

  /**
   * 在方块某个朝向的**外表面**贴一整张精灵，并写入等级染色。
   *
   * 顶点顺序统一为「从该面外侧看：左下 -> 右下 -> 右上 -> 左上」，与
   * [RenderUtil.drawSpriteQuad] 的约定（第一个顶点取精灵的 `U0` / `V1`，即贴图左下）
   * 一致，因此贴图既不会上下颠倒也不会左右镜像 —— 这套映射必须与 Minecraft 烘焙模型
   * （`CubeFace`）的 UV 约定对齐，否则拼出来的边框会错位或镜像。
   *
   * ==为什么这里要自己写顶点，而不用 [RenderUtil.drawSpriteQuad]==
   * 后者把顶点色写死成白色（`setColor(255, 255, 255, 255)`），等于把方块模型的等级染色
   * 整个抹掉。而这些屏幕贴图**通体是纯灰阶**（实测 `screen/f2` 只有 `(13,13,13)` 与
   * `(38,38,38)` 两档主色，`screen/b` 最亮也只到 `(114,114,114)`），等级色完全由顶点色
   * 提供：1 级 `LightGray`、2 级 `Yellow`、3 级 `Cyan`（`Color.byTier`）。所以覆盖层不写
   * 颜色时，**三个等级的屏幕会退化成同一张未染色的深灰贴图** —— 实机反馈的「所有等级的
   * 屏幕被显示为棕色」正是这个现象：深灰贴图在方块光照下偏暖褐，且等级之间不再有任何
   * 颜色差别（原版 1.7.10 靠 `BlockScreen#getRenderColor` 给这些图标染色，1.21.1 靠
   * `tinted_cube` + [li.cil.oc.client.ColorHandlers] 做同一件事）。
   *
   * 几何坐标与原来的 [RenderUtil.drawSpriteQuad] 调用**逐字相同**，本次只改顶点色，
   * 因此不可能引入新的朝向 / UV 偏差。
   */
  private def drawFace(pose: PoseStack, vc: VertexConsumer, side: Direction,
                       sprite: TextureAtlasSprite, tint: Int, light: Int, overlay: Int): Unit = {
    val lo = -overlayEpsilon
    val hi = 1.0 + overlayEpsilon
    side match {
      case Direction.SOUTH => // +Z 面：u 沿 +X，v 沿 -Y。
        drawTintedQuad(pose, vc, sprite, side, tint, 0, 0, hi, 1, 0, hi, 1, 1, hi, 0, 1, hi, light, overlay)
      case Direction.NORTH => // -Z 面：u 沿 -X，v 沿 -Y。
        drawTintedQuad(pose, vc, sprite, side, tint, 1, 0, lo, 0, 0, lo, 0, 1, lo, 1, 1, lo, light, overlay)
      case Direction.EAST => // +X 面：u 沿 -Z，v 沿 -Y。
        drawTintedQuad(pose, vc, sprite, side, tint, hi, 0, 1, hi, 0, 0, hi, 1, 0, hi, 1, 1, light, overlay)
      case Direction.WEST => // -X 面：u 沿 +Z，v 沿 -Y。
        drawTintedQuad(pose, vc, sprite, side, tint, lo, 0, 0, lo, 0, 1, lo, 1, 1, lo, 1, 0, light, overlay)
      case Direction.UP => // +Y 面：u 沿 +X，v 沿 +Z。
        drawTintedQuad(pose, vc, sprite, side, tint, 0, hi, 1, 1, hi, 1, 1, hi, 0, 0, hi, 0, light, overlay)
      case Direction.DOWN => // -Y 面：u 沿 +X，v 沿 -Z。
        drawTintedQuad(pose, vc, sprite, side, tint, 0, lo, 0, 1, lo, 0, 1, lo, 1, 0, lo, 1, light, overlay)
    }
  }

  /**
   * 覆盖层顶点要乘的等级染色值。
   *
   * 正常情况就是 [[Screen]] 方块实体的颜色（构造时 `color = Color.byTier(tier)`），
   * 与 [li.cil.oc.client.ColorHandlers] 给方块模型 `tintindex: 0` 用的是同一个来源，
   * 因此覆盖层与静态模型的颜色严格一致。
   *
   * 兜底：客户端方块实体在收到同步数据之前 `_color` 是 `0`（见
   * `traits.Colored` 的 `readFromNBTForClient`，只有收到 `renderColor` 才会被写入），
   * 直接用会把整块屏幕画成纯黑。此时回退到等级色 —— 语义与方块模型的染色一致，
   * 且不会比「整屏纯黑」更糟。
   */
  private def screenTint(screen: Screen): Int = {
    val color = screen.getColor
    if (color == 0) Color.byTier(screen.tier) else color
  }

  /**
   * 与 [RenderUtil.drawSpriteQuad] 完全等价的一个四边面，唯一区别是顶点色取 `tint`
   * （而不是写死白色）。
   *
   * 顶点顺序与 UV 的对应关系**逐字照搬** [RenderUtil.drawQuad]：
   * 第 1、2 个顶点取 `v1`（贴图下边），第 3、4 个顶点取 `v0`（贴图上边）。
   */
  private def drawTintedQuad(pose: PoseStack, vc: VertexConsumer, sprite: TextureAtlasSprite,
                             normal: Direction, tint: Int,
                             x0: Double, y0: Double, z0: Double,
                             x1: Double, y1: Double, z1: Double,
                             x2: Double, y2: Double, z2: Double,
                             x3: Double, y3: Double, z3: Double,
                             light: Int, overlay: Int): Unit = {
    val entry = pose.last()
    val r = (tint >> 16) & 0xFF
    val g = (tint >> 8) & 0xFF
    val b = tint & 0xFF
    val nx = normal.getStepX.toFloat
    val ny = normal.getStepY.toFloat
    val nz = normal.getStepZ.toFloat
    val u0 = sprite.getU0
    val v0 = sprite.getV0
    val u1 = sprite.getU1
    val v1 = sprite.getV1
    tintedVertex(vc, entry, x0, y0, z0, u0, v1, r, g, b, nx, ny, nz, light, overlay)
    tintedVertex(vc, entry, x1, y1, z1, u1, v1, r, g, b, nx, ny, nz, light, overlay)
    tintedVertex(vc, entry, x2, y2, z2, u1, v0, r, g, b, nx, ny, nz, light, overlay)
    tintedVertex(vc, entry, x3, y3, z3, u0, v0, r, g, b, nx, ny, nz, light, overlay)
  }

  /** 写一个带颜色 / UV / overlay / 光照 / 法线的顶点（与 [RenderUtil] 的私有顶点写法一致）。 */
  private def tintedVertex(vc: VertexConsumer, entry: PoseStack.Pose,
                           x: Double, y: Double, z: Double,
                           u: Float, v: Float,
                           r: Int, g: Int, b: Int,
                           nx: Float, ny: Float, nz: Float,
                           light: Int, overlay: Int): Unit = {
    vc.addVertex(entry, x.toFloat, y.toFloat, z.toFloat)
      .setColor(r, g, b, 255)
      .setUv(u, v)
      .setOverlay(overlay)
      .setLight(light)
      .setNormal(entry, nx, ny, nz)
  }
}
