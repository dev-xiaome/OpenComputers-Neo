package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import org.joml.Vector3f

/**
 * 配电箱（PowerDistributor）方块实体渲染器：顶面与四个侧面盖上工作光效，
 * 光效的**整体透明度**跟随自身缓冲的充能比例变化。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`
 *    （1.21.1 的 `PoseStack` 入场原点已经是方块角）。
 *  - 原实现靠 `glScaled(1.0025, -1.0025, 1.0025)` 把覆盖层顶出方块表面避开 z-fighting；
 *    这里用 `pose.scale` 保留同样的 1.0025 外扩量。
 *  - `RenderState.disableLighting` 删除（1.21.1 无固定管线光照），自发光改用
 *    `RenderUtil.fullBright` 写进顶点光照。
 *  - `Textures.PowerDistributor.iconTopOn/iconSideOn`（`IIcon`）换成
 *    `Textures.Block.PowerDistributorTopOn/PowerDistributorSideOn` 加 `RenderUtil.sprite`。
 *
 * ==UV 与 RenderType 的取舍==
 *  - `powerdistributortopon`（16x16）与 `powerdistributorsideon`（16x224）都是方块图集里的
 *    **动画贴图**，因此只能用方块图集的 RenderType：UV 取自
 *    `TextureAtlasSprite#getU0/getU1`（按当前动画帧返回），不能用指向独立贴图文件的
 *    `entityCutout` 那类 RenderType。
 *  - 每个面都是整张贴图铺满。
 *
 * ==半透明渐隐的取舍（重要）==
 *  - 1.7.10 用 `RenderState.setBlendAlpha(ratio)`（`glColor4f` 加
 *    `glBlendFunc(GL_SRC_ALPHA, GL_ONE)`）让整层光效随充能比例渐显；
 *    1.21.1 里 GL 固定管线的 `glColor4f` 已经不存在，透明度只能写进**顶点颜色**。
 *    `RenderUtil.drawQuad` 固定写死白色不透明，而公共工具是只读的，所以本文件自带一个
 *    `drawTintedSpriteQuad` 辅助方法，用同一套顶点格式把 `alpha` 写进顶点颜色。
 *  - RenderType 选 `RenderType.translucent()`：它自带
 *    `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` 混合，是 1.21.1 里表达「半透明方块图集贴图」的
 *    标准做法。与原实现的 `SRC_ALPHA / ONE`（偏加色）略有差别：在暗背景上两者接近，
 *    在明亮背景上 1.21.1 的观感会更「实」一些。这里优先保证渲染管线正确、不崩，
 *    不追求逐像素一致。
 *  - 顶点颜色里的 alpha 会与 `RenderSystem` 的着色器颜色调制值相乘，正常情况下后者是
 *    (1,1,1,1)（本渲染器不改它，也不再需要 1.7.10 的 `setBlendAlpha`），
 *    所以最终 alpha 就是这里传入的比例值。
 */
class PowerDistributorRenderer extends BlockEntityRenderer[tileentity.PowerDistributor] {

  /** 等价于 1.7.10 的 `GL11.glScaled(1.0025, ...)`。 */
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.PowerDistributor, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    if (t.globalBuffer <= 0) return
    // 原 `distributor.globalBuffer / distributor.globalBufferSize`：
    // 缓冲上限为 0 时（刚放下、还没均衡过）原实现会得到 NaN，这里改成不画，避免脏状态。
    if (t.globalBufferSize <= 0) return
    val ratio = math.max(0.0, math.min(1.0, t.globalBuffer / t.globalBufferSize)).toFloat
    if (ratio <= 0f) return

    val vc = buffer.getBuffer(RenderType.translucent())
    val topOn = RenderUtil.sprite(Textures.Block.PowerDistributorTopOn)
    val sideOn = RenderUtil.sprite(Textures.Block.PowerDistributorSideOn)

    pose.pushPose()
    // 前后各一次 0.5 平移互相抵消，只剩「绕方块中心缩放」。
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 顶面（局部 y = 0）。顶点顺序与原来的 addVertexWithUV 完全一致。
    drawTintedSpriteQuad(pose, vc, topOn, ratio,
      0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, RenderUtil.fullBright, overlay)

    // 北面、南面、东面、西面。
    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, RenderUtil.fullBright, overlay)
    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, RenderUtil.fullBright, overlay)
    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, RenderUtil.fullBright, overlay)
    drawTintedSpriteQuad(pose, vc, sideOn, ratio,
      0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, RenderUtil.fullBright, overlay)

    pose.popPose()
  }

  /**
   * 与 `RenderUtil.drawSpriteQuad` 完全相同的四边形（整张贴图铺满），
   * 但把 `alpha` 作为顶点颜色写进去，用于表达整体渐隐。
   *
   * TODO(渲染): `RenderUtil` 是公共只读文件，无法给它加带颜色的重载；
   * 若后续允许改 `RenderUtil`，应把本方法合并过去并让本渲染器直接调用。
   */
  private def drawTintedSpriteQuad(pose: PoseStack, vc: VertexConsumer, sprite: TextureAtlasSprite,
                                   alpha: Float,
                                   x0: Double, y0: Double, z0: Double,
                                   x1: Double, y1: Double, z1: Double,
                                   x2: Double, y2: Double, z2: Double,
                                   x3: Double, y3: Double, z3: Double,
                                   light: Int, overlay: Int): Unit = {
    if (sprite == null) return
    drawTintedQuad(pose, vc,
      x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
      sprite.getU0, sprite.getV0, sprite.getU1, sprite.getV1,
      alpha, light, overlay)
  }

  /** 带顶点颜色的四边形；顶点顺序与 UV 对应关系与 `RenderUtil.drawQuad` 一致。 */
  private def drawTintedQuad(pose: PoseStack, vc: VertexConsumer,
                             x0: Double, y0: Double, z0: Double,
                             x1: Double, y1: Double, z1: Double,
                             x2: Double, y2: Double, z2: Double,
                             x3: Double, y3: Double, z3: Double,
                             u0: Float, v0: Float, u1: Float, v1: Float,
                             alpha: Float, light: Int, overlay: Int): Unit = {
    val entry = pose.last()
    val mat = entry.pose()
    val a = math.max(0f, math.min(1f, alpha))
    val aByte = (a * 255f).round.toInt

    // 顶点在姿态空间里的位置；法线由前三个顶点在姿态空间里求叉积得到。
    val p0 = mat.transformPosition(x0.toFloat, y0.toFloat, z0.toFloat, new Vector3f())
    val p1 = mat.transformPosition(x1.toFloat, y1.toFloat, z1.toFloat, new Vector3f())
    val p2 = mat.transformPosition(x2.toFloat, y2.toFloat, z2.toFloat, new Vector3f())
    val normal = quadNormal(p0, p1, p2)

    // 四个角的 UV 对应关系与 RenderUtil.drawQuad 相同：(x0,y0,z0) 是贴图右下角。
    vertex(vc, entry, x0, y0, z0, u0, v1, normal, aByte, light, overlay)
    vertex(vc, entry, x1, y1, z1, u1, v1, normal, aByte, light, overlay)
    vertex(vc, entry, x2, y2, z2, u1, v0, normal, aByte, light, overlay)
    vertex(vc, entry, x3, y3, z3, u0, v0, normal, aByte, light, overlay)
  }

  private def vertex(vc: VertexConsumer, entry: PoseStack.Pose,
                     x: Double, y: Double, z: Double,
                     u: Float, v: Float,
                     normal: Vector3f, aByte: Int,
                     light: Int, overlay: Int): Unit = {
    vc.addVertex(entry, x.toFloat, y.toFloat, z.toFloat)
      .setColor(255, 255, 255, aByte)
      .setUv(u, v)
      .setOverlay(overlay)
      .setLight(light)
      .setNormal(entry, normal.x, normal.y, normal.z)
  }

  /** 由三个顶点求法线；退化时退化为 +Y，避免出现 NaN 顶点。 */
  private def quadNormal(p0: Vector3f, p1: Vector3f, p2: Vector3f): Vector3f = {
    val ax = p1.x - p0.x
    val ay = p1.y - p0.y
    val az = p1.z - p0.z
    val bx = p2.x - p0.x
    val by = p2.y - p0.y
    val bz = p2.z - p0.z
    val nx = ay * bz - az * by
    val ny = az * bx - ax * bz
    val nz = ax * by - ay * bx
    val lengthSq = nx * nx + ny * ny + nz * nz
    if (lengthSq < 1e-9f) new Vector3f(0, 1, 0)
    else {
      val inv = (1.0 / math.sqrt(lengthSq.toDouble)).toFloat
      new Vector3f(nx * inv, ny * inv, nz * inv)
    }
  }
}
