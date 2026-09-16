package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.common.tileentity.Screen
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer, BlockEntityRendererProvider}
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.{BlockPos, Direction}
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

    val distance = playerDistanceSq(screen) / math.max(screen.width, screen.height)
    if (distance > maxRenderDistanceSq) return

    // 粗略判断本机玩家是否能看到屏幕正面：屏幕朝外的法线与「玩家 -> 屏幕中心」的夹角。
    val player = Minecraft.getInstance.player
    if (player == null) return
    val screenFacing = screen.facing.getOpposite
    val px = player.getX - (screen.getBlockPos.getX + 0.5)
    val py = player.getY - (screen.getBlockPos.getY + 0.5)
    val pz = player.getZ - (screen.getBlockPos.getZ + 0.5)
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
}
