package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Hologram
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction
import org.joml.Quaternionf

import scala.util.Random

/**
 * 全息投影仪渲染器（原 1.7.10 的 `HologramRenderer`）。
 *
 * 把投影仪内部那张「3×2×3 米、16 像素一格的体素体积」里的实心体素画出来。
 *
 * ==1.7.10 → 1.21.1 的关键取舍==
 * 1.7.10 的实现在这里堆了一大套固定管线优化：
 *  - 一份**共用的静态 VBO**（所有体素 24 个顶点的 UV + 法线 + 位置，`GL_STATIC_DRAW`）；
 *  - 每个投影仪一份**动态 VBO**（每个顶点的 `0xAABBGGRR` 颜色 + 要画的四边形索引，`GL_DYNAMIC_DRAW`）；
 *  - 用 Guava 缓存按 `Hologram` 分配 / 回收 VBO，靠客户端 tick 调 `cleanUp()`；
 *  - 两遍绘制（`glColorMask(false,...)` + `glDepthFunc(GL_EQUAL)`）解决半透明面的前后一致性。
 *
 * 1.21.1 已经没有固定管线、没有 `glInterleavedArrays` / `glDrawElements` /
 * `glColorMask` / `glDepthFunc`，顶点必须写进 `MultiBufferSource` 的 `VertexConsumer`，
 * 两遍遮挡则只能靠 `RenderType`（透明层自带深度排序）。因此这里**刻意放弃 VBO 优化**，
 * 改成逐帧遍历可见体素直接写顶点：
 *  - 复杂度 O(可见四边形数)，和原来两份 VBO 的规模同级；
 *  - 省掉了缓存、tick 清理与显存不足的降级路径（也就顺带不需要
 *    [[HologramRendererFallback]] 那条分支了 —— 它只作为「万一被手动注册」的兜底保留）；
 *  - 视觉上与原版一致：每帧重新求一次「哪些面朝向空气」，
 *    所以 [[Hologram.needsRendering]] 只作为「体积脏了」的提示，不再影响绘制。
 *
 * 顶点颜色来自 `Hologram.colors`（`0xBBGGRR` 打包，见 `Hologram.colorsByTier`），
 * 因此这里手工拆出 RGB 三个通道再交给 `VertexConsumer#setColor`。
 */
class HologramRenderer extends BlockEntityRenderer[Hologram] {

  private val random = new Random()

  override def render(t: Hologram, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null || !t.hasPower) return

    val sprite = RenderUtil.sprite(Textures.Block.HologramEffect)
    if (sprite == null) return

    // 与原实现一致的「离得越远越透明」：先拉近，再在 fade 起点之后线性淡出。
    val playerDistSq = distanceSqToCamera(t)
    val maxDistSq = t.getMaxRenderDistanceSquared
    val fadeDistSq = t.getFadeStartDistanceSquared
    val fade =
      if (playerDistSq > fadeDistSq && maxDistSq > fadeDistSq)
        math.max(0.0, 1.0 - (playerDistSq - fadeDistSq) / (maxDistSq - fadeDistSq))
      else 1.0
    if (fade <= 0.0) return

    val alpha = (0.75f * fade * 255f).toInt.max(0).min(255)

    // 半透明层：1.21.1 的 translucent() 自带深度排序，等价于原来的两遍绘制。
    val vc = buffer.getBuffer(RenderType.translucent())

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 朝向（与原实现完全一致：先 yaw 后 pitch）。
    t.yaw match {
      case Direction.WEST => rotateY(pose, -90f)
      case Direction.NORTH => rotateY(pose, 180f)
      case Direction.EAST => rotateY(pose, 90f)
      case _ => // 南向即默认朝向。
    }
    t.pitch match {
      case Direction.DOWN => rotateX(pose, 90f)
      case Direction.UP => rotateX(pose, -90f)
      case _ =>
    }

    // 手动旋转（`hologram.rotate(...)` / `rotateSpeed(...)` 设置的目标角）。
    rotateAxis(pose, t.rotationAngle, t.rotationX, t.rotationY, t.rotationZ)
    // 持续自转：原实现用 `world.getTotalWorldTime % (360*20 - 1)` 让角度回绕，
    // 1.21.1 的 `getGameTime` 语义相同。
    val level = t.getLevel
    val gameTime = if (level == null) 0L else level.getGameTime
    rotateAxis(pose, t.rotationSpeed * ((gameTime % (360L * 20 - 1) + partialTicks) / 20f),
      t.rotationSpeedX, t.rotationSpeedY, t.rotationSpeedZ)

    // 略微放大，避免与其它方块的表面 z-fighting（原 `glScaled(1.001, ...)`）。
    pose.scale(1.001f, 1.001f, 1.001f)

    // 平移偏移：原实现直接改矩阵，这里保留同样的公式。
    // 注意 `common.tileentity.Hologram.Offset` 目前沿用 1.7.10 的字段名
    // （`xCoord` / `yCoord` / `zCoord`），不是 1.21.1 的 `x` / `y` / `z`。
    pose.translate(
      (t.translation.xCoord * t.width / 16 - 1.5) * t.scale,
      t.translation.yCoord * t.height / 16 * t.scale,
      (t.translation.zCoord * t.width / 16 - 1.5) * t.scale)

    // 抖动：让全息看起来「不太稳」。
    if (Settings.get.hologramFlickerFrequency > 0 && random.nextDouble() < Settings.get.hologramFlickerFrequency) {
      pose.scale((1 + random.nextGaussian() * 0.01).toFloat,
        (1 + random.nextGaussian() * 0.001).toFloat,
        (1 + random.nextGaussian() * 0.01).toFloat)
      pose.translate(random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01)
    }

    // 缩放：让体素坐标 (0..width, 0..height, 0..width) 落到模型空间。
    pose.scale((t.scale / 16f).toFloat, (t.scale / 16f).toFloat, (t.scale / 16f).toFloat)

    drawVolume(t, pose, vc, sprite, alpha, overlay)

    pose.popPose()
  }

  /** 遍历体素体积，把「朝向空气」的面画出来。 */
  private def drawVolume(t: Hologram, pose: PoseStack, vc: VertexConsumer,
                         sprite: TextureAtlasSprite, alpha: Int, overlay: Int): Unit = {
    val w = t.width
    val h = t.height
    val colors = t.colors

    def value(x: Int, y: Int, z: Int): Int =
      if (x >= 0 && y >= 0 && z >= 0 && x < w && y < h && z < w) t.getColor(x, y, z) else 0

    def isSolid(x: Int, y: Int, z: Int): Boolean = value(x, y, z) != 0

    val u0 = sprite.getU0
    val u1 = sprite.getU1
    val v0 = sprite.getV0
    val v1 = sprite.getV1
    val light = RenderUtil.fullBright

    for (x <- 0 until w; z <- 0 until w; y <- 0 until h) {
      val index = value(x, y, z)
      if (index != 0) {
        // `colors` 是 `0xBBGGRR`（见 Hologram.colorsByTier），拆通道时注意顺序。
        val color = colors(math.min(index - 1, colors.length - 1).max(0))
        val r = color & 0xFF
        val g = (color >> 8) & 0xFF
        val b = (color >> 16) & 0xFF

        // 六个方向：只有「邻居是空气」的面才画（原实现的 `addFace` 判据）。
        // 每个面用 drawQuad 显式给 UV，然后手工上色 + 上透明度。
        if (!isSolid(x, y, z + 1)) quad(pose, vc, u0, v0, u1, v1, r, g, b, alpha, light, overlay,
          x + 1, y + 1, z + 1, x, y + 1, z + 1, x, y, z + 1, x + 1, y, z + 1)
        if (!isSolid(x, y, z - 1)) quad(pose, vc, u0, v0, u1, v1, r, g, b, alpha, light, overlay,
          x + 1, y, z, x, y, z, x, y + 1, z, x + 1, y + 1, z)
        if (!isSolid(x + 1, y, z)) quad(pose, vc, u0, v0, u1, v1, r, g, b, alpha, light, overlay,
          x + 1, y + 1, z + 1, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z)
        if (!isSolid(x - 1, y, z)) quad(pose, vc, u0, v0, u1, v1, r, g, b, alpha, light, overlay,
          x, y, z + 1, x, y + 1, z + 1, x, y + 1, z, x, y, z)
        if (!isSolid(x, y + 1, z)) quad(pose, vc, u0, v0, u1, v1, r, g, b, alpha, light, overlay,
          x + 1, y + 1, z, x, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1)
        if (!isSolid(x, y - 1, z)) quad(pose, vc, u0, v0, u1, v1, r, g, b, alpha, light, overlay,
          x + 1, y, z + 1, x, y, z + 1, x, y, z, x + 1, y, z)
      }
    }
  }

  /**
   * 画一个带独立颜色的面。
   *
   * [[RenderUtil.drawQuad]] 固定写白色顶点，而全息必须逐面染色，
   * 因此这里复制它那套顶点写法，只把颜色换掉。
   */
  private def quad(pose: PoseStack, vc: VertexConsumer,
                   u0: Float, v0: Float, u1: Float, v1: Float,
                   r: Int, g: Int, b: Int, a: Int, light: Int, overlay: Int,
                   x0: Double, y0: Double, z0: Double,
                   x1: Double, y1: Double, z1: Double,
                   x2: Double, y2: Double, z2: Double,
                   x3: Double, y3: Double, z3: Double): Unit = {
    val entry = pose.last()
    // 法线由前三个顶点求出；全息的六个面都是轴对齐的，退化时取 +Z。
    val nx0 = x1 - x0
    val ny0 = y1 - y0
    val nz0 = z1 - z0
    val nx1 = x2 - x0
    val ny1 = y2 - y0
    val nz1 = z2 - z0
    var nx = ny0 * nz1 - nz0 * ny1
    var ny = nz0 * nx1 - nx0 * nz1
    var nz = nx0 * ny1 - ny0 * nx1
    val lengthSq = nx * nx + ny * ny + nz * nz
    if (lengthSq < 1e-9) { nx = 0; ny = 0; nz = 1 }
    else {
      val inv = 1.0 / math.sqrt(lengthSq)
      nx *= inv; ny *= inv; nz *= inv
    }
    val fnx = nx.toFloat
    val fny = ny.toFloat
    val fnz = nz.toFloat

    def vertex(x: Double, y: Double, z: Double, u: Float, v: Float): Unit =
      vc.addVertex(entry, x.toFloat, y.toFloat, z.toFloat)
        .setColor(r, g, b, a)
        .setUv(u, v)
        .setOverlay(overlay)
        .setLight(light)
        .setNormal(entry, fnx, fny, fnz)

    vertex(x0, y0, z0, u0, v1)
    vertex(x1, y1, z1, u1, v1)
    vertex(x2, y2, z2, u1, v0)
    vertex(x3, y3, z3, u0, v0)
  }

  private def rotateY(pose: PoseStack, degrees: Float): Unit =
    pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(degrees.toDouble).toFloat, 0f, 1f, 0f))

  private def rotateX(pose: PoseStack, degrees: Float): Unit =
    pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(degrees.toDouble).toFloat, 1f, 0f, 0f))

  /** 绕任意轴旋转；轴为零向量时什么都不做。 */
  private def rotateAxis(pose: PoseStack, degrees: Float, ax: Float, ay: Float, az: Float): Unit = {
    if (degrees == 0f || (ax == 0f && ay == 0f && az == 0f)) return
    pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(degrees.toDouble).toFloat, ax, ay, az))
  }

  /** 方块中心到相机的距离平方（原实现用的是「玩家到方块包围盒」的距离，语义一致）。 */
  private def distanceSqToCamera(t: Hologram): Double = {
    val mc = net.minecraft.client.Minecraft.getInstance()
    if (mc == null || mc.player == null) return 0.0
    val dx = mc.player.getX - (t.getBlockPos.getX + 0.5)
    val dy = mc.player.getY - (t.getBlockPos.getY + 0.5)
    val dz = mc.player.getZ - (t.getBlockPos.getZ + 0.5)
    dx * dx + dy * dy + dz * dz
  }
}
