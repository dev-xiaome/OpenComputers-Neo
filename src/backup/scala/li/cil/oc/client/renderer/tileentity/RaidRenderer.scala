package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.common.tileentity.Raid
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction
import org.joml.Quaternionf

/**
 * RAID（磁盘阵列）前面板的错误 / 活动指示灯渲染器。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - `Tessellator` 加 `GL11` 矩阵，改成 `PoseStack` 加 `VertexConsumer`。
 *  - 贴图是**独立贴图**（1.7.10 用 `bindTexture(Textures.blockRaidFrontXxx)` 绑定，
 *    顶点 UV 直接用 0 到 1 的原始坐标，说明它们不在方块图集里）。1.21.1 因此用
 *    `RenderType.entityTranslucent(...)` 而不是 `RenderType.translucent()`（后者取方块图集）。
 *  - 注意：`Textures.Block.Xxx` 现在还带着历史遗留的 `textures/` 前缀
 *    （那是 1.7.10 `bindTexture` 时代的写法）。1.21.1 里这条路径有两种下游语义，
 *    必须区分清楚，否则实机里会看到紫黑方格（`missingno`）：
 *    图集精灵查询要「相对 `textures/`、不带扩展名」的路径；
 *    而本类需要的是**独立绑定贴图文件**（`RenderType#entityTranslucent` + 0..1 相对 UV），
 *    所以要显式用 [[RenderUtil.blockFile]] 拿到
 *    `opencomputers_neo:textures/block/raidfronterror.png` 这样的完整文件路径。
 *  - `glTranslated(-0.5, 0.5, 0.505)` 加 `glScaled(1, -1, 1)` 的最终几何是
 *    「贴在方块正面外 0.005、以方块中心为原点、y 从 -0.5 到 0.5 的面片」，
 *    这里直接把该结果写进顶点坐标，不使用负缩放（负缩放会翻转三角形绕序）。
 */
class RaidRenderer extends BlockEntityRenderer[Raid] {

  private val u1 = 2 / 16f
  private val fs = 4 / 16f

  // 延迟创建：渲染器由 `client.Proxy` 在客户端初始化阶段构造，
  // 那时 `Settings` 可能还没读完配置。
  private lazy val errorRenderType = RenderType.entityTranslucent(RenderUtil.blockFile("raidfronterror"))
  private lazy val activityRenderType = RenderType.entityTranslucent(RenderUtil.blockFile("raidfrontactivity"))

  override def render(t: Raid, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    val level = t.getLevel

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 与 1.7.10 相同的偏航：把「正面」旋转到方块的朝向。
    t.yaw match {
      case Direction.WEST => rotateY(pose, -90)
      case Direction.NORTH => rotateY(pose, 180)
      case Direction.EAST => rotateY(pose, 90)
      case _ => // 南向即默认朝向，不需要旋转。
    }

    for (slot <- 0 until t.getSlots) {
      if (!t.presence(slot)) {
        // 该槽位没有磁盘：画错误指示灯。
        renderSlot(t, slot, pose, buffer.getBuffer(errorRenderType), overlay)
      }
      else if (System.currentTimeMillis() - t.lastAccess < 400 && level != null &&
        level.getRandom.nextDouble() > 0.1 && slot == (t.lastAccess % t.getSlots).toInt) {
        // 最近访问过的槽位：画活动指示灯（带随机闪烁）。
        renderSlot(t, slot, pose, buffer.getBuffer(activityRenderType), overlay)
      }
    }

    pose.popPose()
  }

  /**
   * 画单个槽位的指示灯面片。
   *
   * 贴图区域与 1.7.10 一致：u 取 `2/16 + slot * 4/16` 起的 4/16 宽，
   * v 铺满整个面片高度（贴图的 v=0 在方块正面上方）。
   */
  private def renderSlot(t: Raid, slot: Int, pose: PoseStack,
                         vc: com.mojang.blaze3d.vertex.VertexConsumer, overlay: Int): Unit = {
    val l = u1 + slot * fs
    val h = u1 + (slot + 1) * fs
    // 顶点顺序「从正面看：左下、右下、右上、左上」；
    // RenderUtil.drawQuad 的 UV 约定是顶点 0、1 取贴图下沿（v1），因此传 v0 = 0、v1 = 1。
    RenderUtil.drawQuad(pose, vc,
      l - 0.5, -0.5, 0.505,
      h - 0.5, -0.5, 0.505,
      h - 0.5, 0.5, 0.505,
      l - 0.5, 0.5, 0.505,
      l, 0f, h, 1f,
      RenderUtil.fullBright, overlay)
  }

  private def rotateY(pose: PoseStack, degrees: Float): Unit = {
    val radians = math.toRadians(degrees).toFloat
    pose.mulPose(new Quaternionf().rotateAxis(radians, 0f, 1f, 0f))
  }
}
