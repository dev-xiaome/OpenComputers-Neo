package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Hologram
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction
import org.joml.Quaternionf

import scala.util.Random

/**
 * 全息投影仪渲染器。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - 原实现把一个 48 x 32 x 48 的体素立方体的所有面预烘焙进一个共享 VBO
 *    （`GL15` 顶点缓冲 + `glInterleavedArrays` + `glDrawElements`），
 *    并把「哪些面可见」的索引缓存在另一个动态 VBO 里。
 *    1.21.1 的顶点数据只能经 `MultiBufferSource` 提交，没有客户端顶点数组，
 *    因此这里改成**逐帧遍历体素**：只绘制「实心且相邻面为空」的面。
 *    体素数最多 48*48*32 = 73728，遍历本身开销很小；面数由内容决定。
 *    被降级掉的是 VBO 缓存与 1.7.10 的两趟透明技巧（先关颜色写找最前片元，
 *    再用 `glDepthFunc(GL_EQUAL)` 画第二遍），详见类尾的说明。
 *  - `GL11.glPushClientAttrib` / `glPushAttrib` / `glColorMask` / `glDepthFunc`
 *    / `GL_NORMALIZE` / `glCullFace` 全部删除，改由 `RenderType` 表达渲染状态。
 *  - 顶点颜色（1.7.10 用 `glColorPointer` 传入打包成 0xBBGGRR 的调色板颜色）
 *    改为逐顶点 `setColor`，颜色分量顺序与原实现一致（低字节 = R）。
 *  - 加色混合（1.7.10 的 `blendFunc(SRC_ALPHA, ONE)`）无法在 client 侧复刻：
 *    1.21.1 里构造自定义 `RenderType` 需要的着色器状态位（如
 *    `ADDITIVE_TRANSPARENCY`）是 `protected` 的，只能退回
 *    `RenderType.translucent()` 的常规 alpha 混合。
 */
class HologramRenderer extends BlockEntityRenderer[Hologram] {
  private val random = new Random()

  /**
   * 渲染距离（方块）。1.21.1 的 `BlockEntityRenderer` 默认只渲染 64 格以内的方块实体，
   * 而全息投影的渲染距离是配置项，这里把它透出去，避免配置大于 64 时被静默截断。
   */
  override def getViewDistance(): Int =
    math.max(64, Settings.get.hologramRenderDistance.toInt)

  override def render(t: Hologram, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null || !t.hasPower) return
    val level = t.getLevel
    if (level == null) return

    val sprite = RenderUtil.sprite(Textures.Block.HologramEffect)
    if (sprite == null) return

    // 距离衰减：1.7.10 用的是「摄像机到方块原点的距离平方」。
    val camera = Minecraft.getInstance.gameRenderer.getMainCamera.getPosition
    val blockPos = t.getBlockPos
    val dx = camera.x - blockPos.getX
    val dy = camera.y - blockPos.getY
    val dz = camera.z - blockPos.getZ
    val playerDistSq = dx * dx + dy * dy + dz * dz
    val maxDistSq = t.getMaxRenderDistanceSquared
    val fadeDistSq = t.getFadeStartDistanceSquared
    val fade =
      if (playerDistSq > fadeDistSq && maxDistSq > fadeDistSq)
        math.max(0.0, 1 - (playerDistSq - fadeDistSq) / (maxDistSq - fadeDistSq))
      else 1.0
    if (fade <= 0) return
    // 1.7.10: setBlendAlpha(0.75f * fade)，逐顶点写入 alpha 表达。
    val alpha = (0.75 * fade * 255).toInt.max(0).min(255)

    // 摄像机在全息投影内部时，1.7.10 会关掉背面剔除；1.21.1 无法按需关闭剔除，
    // 改为在内部时把每个面按两个绕序各写一遍（等价于双面渲染）。
    val doubleSided = t.getRenderBoundingBox.contains(camera)

    val vc = buffer.getBuffer(RenderType.translucent())
    val l = RenderUtil.fullBright

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 朝向（与 1.7.10 相同的偏航 / 俯仰）。
    t.yaw match {
      case Direction.WEST => rotate(pose, -90, 0, 1, 0)
      case Direction.NORTH => rotate(pose, 180, 0, 1, 0)
      case Direction.EAST => rotate(pose, 90, 0, 1, 0)
      case _ => // 南向即默认朝向，不需要旋转。
    }
    t.pitch match {
      case Direction.DOWN => rotate(pose, 90, 1, 0, 0)
      case Direction.UP => rotate(pose, -90, 1, 0, 0)
      case _ => // 无俯仰。
    }

    // 基础旋转 + 随时间旋转（原实现按世界时间求当前角度）。
    rotate(pose, t.rotationAngle, t.rotationX, t.rotationY, t.rotationZ)
    val ticks = level.getGameTime % (360L * 20 - 1) + partialTicks
    rotate(pose, t.rotationSpeed * ticks / 20f, t.rotationSpeedX, t.rotationSpeedY, t.rotationSpeedZ)

    // 避免与其它方块 z-fighting。
    pose.scale(1.001f, 1.001f, 1.001f)

    // 投影偏移（1.7.10 里的 `translation*` + `scale` 组合）。
    pose.translate(
      (t.translation.xCoord * t.width / 16 - 1.5) * t.scale,
      t.translation.yCoord * t.height / 16 * t.scale,
      (t.translation.zCoord * t.width / 16 - 1.5) * t.scale)

    // 全息嘛，总得闪一下。
    if (Settings.get.hologramFlickerFrequency > 0 && random.nextDouble() < Settings.get.hologramFlickerFrequency) {
      pose.scale(
        (1 + random.nextGaussian() * 0.01).toFloat,
        (1 + random.nextGaussian() * 0.001).toFloat,
        (1 + random.nextGaussian() * 0.01).toFloat)
      pose.translate(random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01)
    }

    // 缩放后，全息投影占据 [0..width] x [0..height] x [0..width] 的立方体。
    val voxelScale = (t.scale / 16.0).toFloat
    pose.scale(voxelScale, voxelScale, voxelScale)

    val visibleQuads = drawVoxels(t, pose, vc, sprite, alpha, l, overlay, doubleSided)
    // 与原实现一致：把可见面数写回方块实体（供调试 / 数据统计使用）。
    t.visibleQuads = visibleQuads

    pose.popPose()
  }

  // ----------------------------------------------------------------------- //
  // 体素
  // ----------------------------------------------------------------------- //

  /**
   * 遍历体素并绘制暴露在外的面，返回绘制的面数。
   *
   * 面与顶点的定义完全照搬 1.7.10 预烘焙的 VBO 数据
   * （顶点顺序、法线、UV 的 0/1 排布都保持一致），只是改成每帧现算。
   */
  private def drawVoxels(t: Hologram, pose: PoseStack, vc: VertexConsumer, sprite: TextureAtlasSprite,
                         alpha: Int, light: Int, overlay: Int, doubleSided: Boolean): Int = {
    val width = t.width
    val height = t.height
    var count = 0

    // 越界视为空气（与 1.7.10 的 `value` 辅助函数一致）。
    def value(x: Int, y: Int, z: Int): Int =
      if (x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < width) t.getColor(x, y, z) else 0

    def solid(x: Int, y: Int, z: Int): Boolean = value(x, y, z) != 0

    for (x <- 0 until width) {
      for (z <- 0 until width) {
        for (y <- 0 until height) {
          val v = value(x, y, z)
          if (v != 0) {
            val color = t.colors(v - 1)
            val r = color & 0xFF
            val g = (color >>> 8) & 0xFF
            val b = (color >>> 16) & 0xFF

            if (!solid(x, y, z + 1)) {
              face(pose, vc, sprite, 0, 0, 1,
                x + 1, y + 1, z + 1, x, y + 1, z + 1, x, y, z + 1, x + 1, y, z + 1,
                r, g, b, alpha, light, overlay, doubleSided)
              count += 1
            }
            if (!solid(x, y, z - 1)) {
              face(pose, vc, sprite, 0, 0, -1,
                x + 1, y, z, x, y, z, x, y + 1, z, x + 1, y + 1, z,
                r, g, b, alpha, light, overlay, doubleSided)
              count += 1
            }
            if (!solid(x + 1, y, z)) {
              face(pose, vc, sprite, 1, 0, 0,
                x + 1, y + 1, z + 1, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z,
                r, g, b, alpha, light, overlay, doubleSided)
              count += 1
            }
            if (!solid(x - 1, y, z)) {
              face(pose, vc, sprite, -1, 0, 0,
                x, y, z + 1, x, y + 1, z + 1, x, y + 1, z, x, y, z,
                r, g, b, alpha, light, overlay, doubleSided)
              count += 1
            }
            if (!solid(x, y + 1, z)) {
              face(pose, vc, sprite, 0, 1, 0,
                x + 1, y + 1, z, x, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1,
                r, g, b, alpha, light, overlay, doubleSided)
              count += 1
            }
            if (!solid(x, y - 1, z)) {
              face(pose, vc, sprite, 0, -1, 0,
                x + 1, y, z + 1, x, y, z + 1, x, y, z, x + 1, y, z,
                r, g, b, alpha, light, overlay, doubleSided)
              count += 1
            }
          }
        }
      }
    }

    count
  }

  /**
   * 写一个面。
   *
   * UV 排布与 1.7.10 的 VBO 一致：顶点 0、1、2、3 分别取贴图的
   * (minU,minV)、(maxU,minV)、(maxU,maxV)、(minU,maxV)。
   *
   * `doubleSided` 为真时（摄像机在全息投影内部）再按相反绕序写一遍，
   * 这样即使 `RenderType` 开着背面剔除也能从内部看到。
   */
  private def face(pose: PoseStack, vc: VertexConsumer, sprite: TextureAtlasSprite,
                   nx: Float, ny: Float, nz: Float,
                   x0: Double, y0: Double, z0: Double,
                   x1: Double, y1: Double, z1: Double,
                   x2: Double, y2: Double, z2: Double,
                   x3: Double, y3: Double, z3: Double,
                   r: Int, g: Int, b: Int, alpha: Int, light: Int, overlay: Int,
                   doubleSided: Boolean): Unit = {
    val p = pose.last()
    val u0 = sprite.getU0
    val u1 = sprite.getU1
    val v0 = sprite.getV0
    val v1 = sprite.getV1

    vertex(vc, p, x0, y0, z0, u0, v0, nx, ny, nz, r, g, b, alpha, light, overlay)
    vertex(vc, p, x1, y1, z1, u1, v0, nx, ny, nz, r, g, b, alpha, light, overlay)
    vertex(vc, p, x2, y2, z2, u1, v1, nx, ny, nz, r, g, b, alpha, light, overlay)
    vertex(vc, p, x3, y3, z3, u0, v1, nx, ny, nz, r, g, b, alpha, light, overlay)

    if (doubleSided) {
      // 反绕序 + 反法线：从内侧看是正面。
      vertex(vc, p, x0, y0, z0, u0, v0, -nx, -ny, -nz, r, g, b, alpha, light, overlay)
      vertex(vc, p, x3, y3, z3, u0, v1, -nx, -ny, -nz, r, g, b, alpha, light, overlay)
      vertex(vc, p, x2, y2, z2, u1, v1, -nx, -ny, -nz, r, g, b, alpha, light, overlay)
      vertex(vc, p, x1, y1, z1, u1, v0, -nx, -ny, -nz, r, g, b, alpha, light, overlay)
    }
  }

  private def vertex(vc: VertexConsumer, p: PoseStack.Pose,
                     x: Double, y: Double, z: Double, u: Float, v: Float,
                     nx: Float, ny: Float, nz: Float,
                     r: Int, g: Int, b: Int, alpha: Int, light: Int, overlay: Int): Unit = {
    vc.addVertex(p, x.toFloat, y.toFloat, z.toFloat)
      .setColor(r, g, b, alpha)
      .setUv(u, v)
      .setOverlay(overlay)
      .setLight(light)
      .setNormal(p, nx, ny, nz)
  }

  // ----------------------------------------------------------------------- //
  // 变换
  // ----------------------------------------------------------------------- //

  /**
   * 绕任意轴旋转。
   *
   * 1.7.10 用 `GL11.glRotatef(angle, x, y, z)`：轴会被归一化，角度是**度**。
   * 这里显式归一化后交给 JOML（零向量直接跳过，避免出现 NaN 顶点）。
   */
  private def rotate(pose: PoseStack, angle: Float, x: Float, y: Float, z: Float): Unit = {
    if (angle == 0f) return
    val length = math.sqrt(x * x + y * y + z * z).toFloat
    if (length < 1e-6f) return
    val radians = math.toRadians(angle.toDouble).toFloat
    pose.mulPose(new Quaternionf().rotateAxis(radians, x / length, y / length, z / length))
  }
}
