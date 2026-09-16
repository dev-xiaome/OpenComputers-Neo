package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Charger
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction

/**
 * 充电器（Charger）方块实体渲染器：画正面「充电进度」覆盖层与通电时的侧面光效。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`
 *    （1.21.1 的 `PoseStack` 入场原点已经是方块角）。
 *  - `glRotatef(±90 / 180, 0, 1, 0)` 换成 `pose.mulPose(Axis.YP.rotationDegrees(...))`。
 *  - `RenderState.disableLighting/makeItBlend/setBlendAlpha`、`glPushAttrib/glPopAttrib`
 *    整体删除；自发光改由 `RenderUtil.fullBright` 写进顶点。
 *  - 一张 `bindTexture` 加多次 `addVertexWithUV` 的批量绘制，换成同一
 *    `VertexConsumer`（`RenderType.cutout()`）上的多次 `RenderUtil.drawQuad`。
 *
 * ==UV 与 RenderType 的取舍==
 *  - `chargerfronton`（16x16）与 `chargersideon`（16x224 动画贴图）都带二值 alpha，
 *    所以统一用 `RenderType.cutout()`：留在方块图集（能取 `TextureAtlasSprite` 的当前帧）
 *    同时做 alpha 测试。不用 `solid()`（透明像素会变黑块），也不用 `translucent()`
 *    （没有真正的半透明像素，反而引入排序与深度写入问题）。
 *  - 正面进度条是「贴图的**下半部分**」：1.7.10 用
 *    `frontIcon.getInterpolatedV(inverse * 16)` 把 V 插值到 `inverse` 位置。
 *    1.21.1 换成 `TextureAtlasSprite#getV(inverse)`（参数是当前帧内 0 到 1 的比例，
 *    与原像素值除以 16 等价），因此可以继续用 `drawQuad` 表达部分 UV。
 *  - 侧面光效是整张贴图铺满 → `drawSpriteQuad`。
 */
class ChargerRenderer extends BlockEntityRenderer[Charger] {

  override def render(t: Charger, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    if (t.chargeSpeed <= 0) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val frontSprite = RenderUtil.sprite(Textures.Block.ChargerFrontOn)
    val sideSprite = RenderUtil.sprite(Textures.Block.ChargerSideOn)

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 正面朝向 = 方块朝向的反方向（与 1.7.10 的 yaw 分支一致）。
    t.yaw match {
      case Direction.WEST => pose.mulPose(Axis.YP.rotationDegrees(-90f))
      case Direction.NORTH => pose.mulPose(Axis.YP.rotationDegrees(180f))
      case Direction.EAST => pose.mulPose(Axis.YP.rotationDegrees(90f))
      case _ => // 无偏航（SOUTH）。
    }

    // 原 `glTranslatef(-0.5, 0.5, 0.5)` 加 `glScalef(1, -1, 1)`：
    // 把原点挪到「正面在 z = 0、上边在 y = 0」的位置，并用负 Y 缩放抵消纹理 V 轴朝下。
    pose.translate(-0.5, 0.5, 0.5)
    pose.scale(1f, -1f, 1f)

    // 正面进度条：上边固定，下边随 chargeSpeed 下移（inverse = 1 - chargeSpeed）。
    if (frontSprite != null) {
      val inverse = (1 - t.chargeSpeed).toFloat
      RenderUtil.drawQuad(pose, vc,
        0, 1, 0.005,
        1, 1, 0.005,
        1, inverse, 0.005,
        0, inverse, 0.005,
        frontSprite.getU0, frontSprite.getV0, frontSprite.getU1, frontSprite.getV(inverse),
        RenderUtil.fullBright, overlay)
    }

    // 通电时的三个侧面光效（整张贴图铺满）。
    if (t.hasPower) {
      RenderUtil.drawSpriteQuad(pose, vc, sideSprite,
        -0.005, 1, -1, -0.005, 1, 0, -0.005, 0, 0, -0.005, 0, -1,
        RenderUtil.fullBright, overlay)
      RenderUtil.drawSpriteQuad(pose, vc, sideSprite,
        1, 1, -1.005, 0, 1, -1.005, 0, 0, -1.005, 1, 0, -1.005,
        RenderUtil.fullBright, overlay)
      RenderUtil.drawSpriteQuad(pose, vc, sideSprite,
        1.005, 1, 0, 1.005, 1, -1, 1.005, 0, -1, 1.005, 0, 0,
        RenderUtil.fullBright, overlay)
    }

    pose.popPose()
  }
}
