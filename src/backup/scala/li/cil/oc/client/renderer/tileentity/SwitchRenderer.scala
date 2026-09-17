package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.traits.SwitchLike
import li.cil.oc.common.tileentity.BlockEntityBase
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}

/**
 * 交换机 / 中继器 / 无线接入点的活动指示灯渲染器。
 *
 * 1.7.10 里由 `AccessPoint`、`Relay`、`Switch` 三个方块共用同一份 `SwitchRenderer`；
 * 1.21.1 由 `client.Proxy` 把同一个渲染器注册给三个方块实体类型，语义不变。
 * 三者的公共超类型是 `BlockEntityBase` 与 `traits.SwitchLike` 的交集
 * （`AccessPoint` 继承 `Switch`，而 `Relay` 直接继承 `BlockEntityBase`，
 * 所以没有共同的具名方块实体基类，这里用交集类型表达）。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - `Tessellator` + `GL11`，改成 `PoseStack` + `VertexConsumer`。
 *  - 只叠四个侧面（上下面留给线缆），几何与 1.7.10 一致。
 *  - alpha 改为逐顶点写入（原因见 [[TransposerRenderer]] 的说明）；
 *    不再依赖 `RenderState.setBlendAlpha` 这类全局颜色调用。
 *  - 原来的 `glScaled(1.0025, -1.0025, 1.0025)` 中 y 轴取反是为了修正 1.7.10 的
 *    贴图方向，1.21.1 用 UV 顺序表达同一效果，不引入负缩放（负缩放会翻转三角形绕序）。
 */
class SwitchRenderer extends BlockEntityRenderer[BlockEntityBase with SwitchLike] {

  override def render(t: BlockEntityBase with SwitchLike, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    // 原实现：最后一次网络活动后 1 秒内线性淡出。
    val activity = math.max(0.0, 1 - (System.currentTimeMillis() - t.lastMessage) / 1000.0)
    if (activity <= 0) return

    val sprite = RenderUtil.sprite(Textures.Block.SwitchSideOn)
    if (sprite == null) return

    val alpha = (activity * 255).toInt.max(0).min(255)
    val vc = buffer.getBuffer(RenderType.translucent())
    val l = RenderUtil.fullBright

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(1.0025f, 1.0025f, 1.0025f)
    pose.translate(-0.5, -0.5, -0.5)

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

  /** 顶点顺序「从外侧看：左下、右下、右上、左上」，UV 顺序与 RenderUtil.drawQuad 一致。 */
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
