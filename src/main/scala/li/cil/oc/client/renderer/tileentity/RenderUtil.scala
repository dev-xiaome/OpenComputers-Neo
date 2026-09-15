package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.{TextureAtlas, TextureAtlasSprite}
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.joml.Vector3f

/**
 * 方块实体渲染器共用工具。
 *
 * 1.7.10 里每个渲染器都自己拿 `Tessellator.instance` 手写 `addVertexWithUV`；
 * 1.21.1 改成 `PoseStack` + `VertexConsumer`，顶点必须显式给出
 * 「位置 + 颜色 + UV + overlay + 光照 + 法线」，因此把这段样板集中到这里，
 * 各个渲染器只需要描述四方形的四个角。
 *
 * 纹理一律通过方块图集按需查询（[sprite]）：NeoForge 1.21.1 已经没有
 * `TextureStitchEvent`，不能再像 1.7.10 那样在贴图注册事件里缓存 `IIcon`。
 */
object RenderUtil {
  /** 原实现：按哈希值让「错误指示灯」以 500ms 为周期闪烁。 */
  def shouldShowErrorLight(hash: Int): Boolean = {
    val time = System.currentTimeMillis + hash
    val timeSlice = time / 500
    timeSlice % 2 == 0
  }

  // ----------------------------------------------------------------------- //
  // 顶点
  // ----------------------------------------------------------------------- //

  /**
   * 画一个带 UV 的四边形。
   *
   * 顶点顺序必须与 [u0, v0] → [u1, v1] 的对应关系一致；四个角按逆时针给出
   * （从四边形的正面看），法线由前三个顶点自动求出。
   *
   * @param x0..z3      四个角的位置，顺序为左下 / 右下 / 右上 / 左上
   * @param u0,v0,u1,v1 纹理坐标，`u0/v0` 对应第一个角，`u1/v1` 对应第三个角
   * @param light       打包后的光照值（`blockLight << 16 | skyLight << 8`），
   *                    用 [fullBright] 表示自发光
   * @param overlay     覆盖层（通常直接透传渲染器拿到的 overlay）
   */
  def drawQuad(pose: PoseStack,
               vc: VertexConsumer,
               x0: Double, y0: Double, z0: Double,
               x1: Double, y1: Double, z1: Double,
               x2: Double, y2: Double, z2: Double,
               x3: Double, y3: Double, z3: Double,
               u0: Float, v0: Float, u1: Float, v1: Float,
               light: Int, overlay: Int): Unit = {
    val poseEntry = pose.last()
    val normal = quadNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)
    vertex(vc, poseEntry, x0, y0, z0, u0, v1, normal, light, overlay)
    vertex(vc, poseEntry, x1, y1, z1, u1, v1, normal, light, overlay)
    vertex(vc, poseEntry, x2, y2, z2, u1, v0, normal, light, overlay)
    vertex(vc, poseEntry, x3, y3, z3, u0, v0, normal, light, overlay)
  }

  /**
   * 画一个轴对齐的四边形，UV 铺满整张贴图。
   *
   * 这是 1.7.10 里 `addVertexWithUV(x, y, z, icon.getMinU, icon.getMinV)` 那种写法的直译：
   * 贴图完整贴在四边形上。
   */
  def drawSpriteQuad(pose: PoseStack,
                     vc: VertexConsumer,
                     sprite: TextureAtlasSprite,
                     x0: Double, y0: Double, z0: Double,
                     x1: Double, y1: Double, z1: Double,
                     x2: Double, y2: Double, z2: Double,
                     x3: Double, y3: Double, z3: Double,
                     light: Int, overlay: Int): Unit = {
    if (sprite == null) return
    drawQuad(pose, vc,
      x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
      sprite.getU0, sprite.getV0, sprite.getU1, sprite.getV1,
      light, overlay)
  }

  /**
   * 画一个正方体的六个面（用于「整块被覆盖」这类效果）。
   *
   * `inset` 会从六个方向各向内缩一点，避免与方块本体的面完全重合导致 z-fighting
   * （1.7.10 里是靠 `GL11.glScaled(1.0025, ...)` 之类的手法解决的）。
   */
  def drawCube(pose: PoseStack,
               vc: VertexConsumer,
               sprite: TextureAtlasSprite,
               inset: Double,
               light: Int, overlay: Int): Unit = {
    if (sprite == null) return
    val lo = inset
    val hi = 1.0 - inset
    // 下面（-Y）
    drawSpriteQuad(pose, vc, sprite, lo, lo, lo, hi, lo, hi, lo, lo, hi, hi, lo, lo, light, overlay)
    // 上面（+Y）
    drawSpriteQuad(pose, vc, sprite, lo, hi, lo, lo, hi, hi, hi, hi, hi, hi, hi, lo, light, overlay)
    // 北面（-Z）
    drawSpriteQuad(pose, vc, sprite, lo, lo, lo, hi, lo, lo, hi, hi, lo, lo, hi, lo, light, overlay)
    // 南面（+Z）
    drawSpriteQuad(pose, vc, sprite, hi, lo, hi, lo, lo, hi, lo, hi, hi, hi, hi, hi, light, overlay)
    // 西面（-X）
    drawSpriteQuad(pose, vc, sprite, lo, lo, hi, lo, lo, lo, lo, hi, lo, lo, hi, hi, light, overlay)
    // 东面（+X）
    drawSpriteQuad(pose, vc, sprite, hi, lo, lo, hi, lo, hi, hi, hi, hi, hi, hi, lo, light, overlay)
  }

  private def vertex(vc: VertexConsumer,
                     pose: PoseStack.Pose,
                     x: Double, y: Double, z: Double,
                     u: Float, v: Float,
                     normal: Vector3f,
                     light: Int, overlay: Int): Unit = {
    vc.addVertex(pose, x.toFloat, y.toFloat, z.toFloat)
      .setColor(255, 255, 255, 255)
      .setUv(u, v)
      .setOverlay(overlay)
      .setLight(light)
      .setNormal(pose, normal.x, normal.y, normal.z)
  }

  /** 由三个顶点求四边形法线；退化时退化为 +Y，避免出现 NaN 顶点。 */
  private def quadNormal(x0: Double, y0: Double, z0: Double,
                         x1: Double, y1: Double, z1: Double,
                         x2: Double, y2: Double, z2: Double): Vector3f = {
    val ax = x1 - x0
    val ay = y1 - y0
    val az = z1 - z0
    val bx = x2 - x0
    val by = y2 - y0
    val bz = z2 - z0
    val nx = ay * bz - az * by
    val ny = az * bx - ax * bz
    val nz = ax * by - ay * bx
    val lengthSq = nx * nx + ny * ny + nz * nz
    if (lengthSq < 1e-9) new Vector3f(0, 1, 0)
    else {
      val inv = (1.0 / math.sqrt(lengthSq)).toFloat
      new Vector3f((nx * inv).toFloat, (ny * inv).toFloat, (nz * inv).toFloat)
    }
  }

  /** 全亮光照值（自发光面用，等价于 1.7.10 里关掉光照后直接上色）。 */
  val fullBright: Int = 0xF000F0

  // ----------------------------------------------------------------------- //
  // 贴图
  // ----------------------------------------------------------------------- //

  /**
   * 取方块图集里的精灵。
   *
   * TODO(渲染): 图集尚未加载完成时返回 `null`（调用方需要判空）。
   * 1.7.10 的 `IIcon` 在贴图注册事件里一次性拿好，1.21.1 改成按需查询，
   * 因此资源包重载后拿到的一定是新精灵，不需要额外的失效逻辑。
   */
  def sprite(rl: ResourceLocation): TextureAtlasSprite = {
    if (rl == null) return null
    val mc = Minecraft.getInstance
    if (mc == null) return null
    val atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
    if (atlas == null) null else atlas.apply(rl)
  }

  /** 命名空间为 `opencomputers_neo` 的方块贴图位置。 */
  def blockTexture(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "block/" + name)

  /** 命名空间为 `opencomputers_neo` 的模型贴图位置。 */
  def modelTexture(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "model/" + name)

  // ----------------------------------------------------------------------- //
  // 光照
  // ----------------------------------------------------------------------- //

  /**
   * 方块实体的打包光照值。
   *
   * 1.7.10 的渲染器通过 `RenderState.enableLighting/disableLighting` 切换固定管线光照；
   * 1.21.1 改成把光照值写进顶点，所以这里给出「以方块自身位置取光照」的取法。
   */
  def lightAt(level: Level, pos: BlockPos): Int = {
    if (level == null || pos == null) return fullBright
    val state = level.getBlockState(pos)
    val light = if (state != null && state.canOcclude()) level.getLightEmission(pos) else 0
    net.minecraft.client.renderer.LightTexture.pack(light, light)
  }
}
