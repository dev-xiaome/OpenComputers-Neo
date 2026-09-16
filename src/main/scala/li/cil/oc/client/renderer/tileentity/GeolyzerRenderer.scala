package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}

/**
 * 地形分析仪（Geolyzer）方块实体渲染器：在顶面画一层工作光效。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`
 *    （1.21.1 的 `PoseStack` 入场原点已经是方块角）。
 *  - 原实现靠 `glScaled(1.0025, -1.0025, 1.0025)` 把覆盖层顶出方块表面避开 z-fighting；
 *    这里用 `pose.scale` 保留同样的 1.0025 外扩量。
 *  - `RenderState.disableLighting/makeItBlend/setBlendAlpha`、`glPushAttrib/glPopAttrib`
 *    整体删除；自发光改由 `RenderUtil.fullBright` 写进顶点。
 *  - `Textures.Geolyzer.iconTopOn`（`IIcon`）换成 `Textures.Block.GeolyzerTopOn`
 *    加 `RenderUtil.sprite`（可能为 `null`，`drawSpriteQuad` 内部已判空）。
 *
 * ==UV 与 RenderType 的取舍==
 *  - `geolyzertopon`（16x224）是方块图集里的**动画贴图**，所以只能配
 *    `RenderType.cutout()`（方块图集加方块顶点格式），不能用指向独立贴图文件的
 *    `entityCutout` 那类 RenderType。
 *  - 它带二值 alpha，故用 `cutout()` 而非 `solid()`（后者不做 alpha 测试，
 *    透明像素会渲染成黑块）。
 *  - 整张贴图铺满顶面，直接用 `drawSpriteQuad` 就是对 1.7.10
 *    `icon.getMinU/getMaxU`（按当前帧返回）写法的直译。
 *
 * ==注意==
 *  - 原实现对**任何**地形分析仪都画这层光效（不看方块状态）；1.21.1 保持一致，
 *    不额外引入 `isActive` 之类的判断。
 */
class GeolyzerRenderer extends BlockEntityRenderer[tileentity.Geolyzer] {

  /** 等价于 1.7.10 的 `GL11.glScaled(1.0025, ...)`。 */
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.Geolyzer, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val topOn = RenderUtil.sprite(Textures.Block.GeolyzerTopOn)

    pose.pushPose()
    // 前后各一次 0.5 平移互相抵消，只剩「绕方块中心缩放」。
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 顶面（局部 y = 0）；顶点顺序与原来的 addVertexWithUV 完全一致。
    RenderUtil.drawSpriteQuad(pose, vc, topOn,
      0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, RenderUtil.fullBright, overlay)

    pose.popPose()
  }
}
