package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}

/**
 * 交换器（物品/流体搬运）的活动指示灯渲染器。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - `Tessellator.instance` 加 `GL11` 矩阵/颜色，改成 `PoseStack` 加 `VertexConsumer`；
 *    几何与 1.7.10 完全一致（整块外表面略微放大 1.0025 叠一层 TransposerOn 贴图）。
 *  - 不再使用 `RenderState.setBlendAlpha`：它内部走全局着色器颜色，
 *    而延迟批处理的 `MultiBufferSource` 要到 flush 时才应用着色器，
 *    全局颜色会被之后的渲染覆盖或污染别的批次。改为把 alpha 逐顶点写进顶点颜色。
 *  - `GL11.glDisable(GL11.GL_LIGHTING)` 删除；自发光面直接写 `RenderUtil.fullBright`。
 */
class TransposerRenderer extends BlockEntityRenderer[tileentity.Transposer] {

  override def render(t: tileentity.Transposer, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    // 原实现：搬运成功后 1 秒内线性淡出。
    val activity = math.max(0.0, 1 - (System.currentTimeMillis() - t.lastOperation) / 1000.0)
    if (activity <= 0) return

    val sprite = RenderUtil.sprite(Textures.Block.TransposerOn)
    if (sprite == null) return

    val alpha = (activity * 255).toInt.max(0).min(255)
    val vc = buffer.getBuffer(RenderType.translucent())
    val l = RenderUtil.fullBright

    pose.pushPose()
    // 略微放大，避免与方块本体的面完全重合导致 z-fighting（等价于 1.7.10 的
    // glScaled(1.0025, -1.0025, 1.0025)；1.21.1 不需要 y 轴取反，贴图方向由 UV 顺序保证）。
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(1.0025f, 1.0025f, 1.0025f)
    pose.translate(-0.5, -0.5, -0.5)

    // 下面（-Y）
    quad(pose, vc, sprite, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0, alpha, l, overlay)
    // 上面（+Y）
    quad(pose, vc, sprite, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, alpha, l, overlay)
    // 北面（-Z）
    quad(pose, vc, sprite, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, -1, alpha, l, overlay)
    // 南面（+Z）
    quad(pose, vc, sprite, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1, alpha, l, overlay)
    // 西面（-X）
    quad(pose, vc, sprite, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0, alpha, l, overlay)
    // 东面（+X）
    quad(pose, vc, sprite, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, alpha, l, overlay)

    pose.popPose()
  }

  /**
   * 写一个朝外的四边形。
   *
   * 顶点顺序为「从外侧看：左下、右下、右上、左上」，因此法线朝外；
   * UV 顺序与 RenderUtil.drawQuad 一致（顶点 0、1 取贴图下沿），保证贴图正立。
   */
  private def quad(pose: PoseStack, vc: VertexConsumer, sprite: TextureAtlasSprite,
                   x0: Double, y0: Double, z0: Double,
                   x1: Double, y1: Double, z1: Double,
                   x2: Double, y2: Double, z2: Double,
                   x3: Double, y3: Double, z3: Double,
                   nx: Float, ny: Float, nz: Float,
                   alpha: Int, light: Int, overlay: Int): Unit = {
    val p = pose.last()
    val u0 = sprite.getU0
    val u1 = sprite.getU1
    val v0 = sprite.getV0
    val v1 = sprite.getV1
    vertex(vc, p, x0, y0, z0, u0, v1, nx, ny, nz, alpha, light, overlay)
    vertex(vc, p, x1, y1, z1, u1, v1, nx, ny, nz, alpha, light, overlay)
    vertex(vc, p, x2, y2, z2, u1, v0, nx, ny, nz, alpha, light, overlay)
    vertex(vc, p, x3, y3, z3, u0, v0, nx, ny, nz, alpha, light, overlay)
  }

  private def vertex(vc: VertexConsumer, p: PoseStack.Pose,
                     x: Double, y: Double, z: Double, u: Float, v: Float,
                     nx: Float, ny: Float, nz: Float,
                     alpha: Int, light: Int, overlay: Int): Unit = {
    vc.addVertex(p, x.toFloat, y.toFloat, z.toFloat)
      .setColor(255, 255, 255, alpha)
      .setUv(u, v)
      .setOverlay(overlay)
      .setLight(light)
      .setNormal(p, nx, ny, nz)
  }
}
