package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}

/**
 * 拆解机（Disassembler）方块实体渲染器：工作时给顶面与四个侧面盖上光效。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`
 *    （1.21.1 的 `PoseStack` 入场原点已经是方块角）。
 *  - `glScaled(1.0025, -1.0025, 1.0025)` 换成 `pose.scale(s, -s, s)`，**y 轴必须保持取负**：
 *    原来的四角坐标按「y 被镜像」的空间书写（顶面写在局部 y = 0），去掉负号会让顶面光效
 *    跑到方块底面，而且三角形绕序反过来被背面剔除（整层光效直接看不见）。
 *  - `RenderState.disableLighting/makeItBlend`、`glPushAttrib/glPopAttrib` 整体删除；
 *    自发光改由 `RenderUtil.fullBright` 写进顶点光照。
 *  - `IIcon`（`Textures.Disassembler.iconTopOn` / `iconSideOn`）换成
 *    `Textures.Block.DisassemblerTopOn` / `DisassemblerSideOn` 加 `RenderUtil.sprite`
 *    （可能为 `null`，`drawSpriteQuad` 内部已判空）。
 *
 * ==UV 与 RenderType 的取舍==
 *  - `disassemblertopon`（16x64，纵向 4 帧）与 `disassemblersideon`（16x80，5 帧动画，
 *    帧序列在 mcmeta 里重排）都是方块图集里的**动画贴图**，所以只能用
 *    `RenderType.cutout()`（方块图集加方块顶点格式），不能用指向独立贴图文件的
 *    `entityCutout`。
 *  - 两者都带二值 alpha（逐像素核验过：只有 0 与 255），故用 `cutout()` 而非 `solid()`
 *    （后者不做 alpha 测试，透明像素会渲染成黑块）；也与原来的 `makeItBlend` 观感一致。
 *  - 五个面的四角 UV 顺序在 1.7.10 里本来就是 `drawQuad` 认识的标准顺序
 *    （`(minU, maxV)`、`(maxU, maxV)`、`(maxU, minV)`、`(minU, minV)`），
 *    所以四角坐标与调用顺序可以逐字照抄；`getMinU/getMaxU` 只覆盖当前动画帧，
 *    与 `TextureAtlasSprite#getU0/getU1` 语义相同。
 */
class DisassemblerRenderer extends BlockEntityRenderer[tileentity.Disassembler] {

  /** 等价于 1.7.10 的 `GL11.glScaled(1.0025, ...)`。 */
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.Disassembler, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    if (!t.isActive) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val topOn = RenderUtil.sprite(Textures.Block.DisassemblerTopOn)
    val sideOn = RenderUtil.sprite(Textures.Block.DisassemblerSideOn)

    pose.pushPose()
    // 与原实现逐句对应：0.5 平移 / 等比缩放（y 取负）/ -0.5 平移（把缩放中心挪回方块中心）。
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, -OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 顶面（局部 y = 0，y 取负后落到方块顶面）；顶点顺序与原来的 addVertexWithUV 完全一致。
    RenderUtil.drawSpriteQuad(pose, vc, topOn,
      0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, RenderUtil.fullBright, overlay)

    // 北面、南面、东面、西面。
    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, RenderUtil.fullBright, overlay)
    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, RenderUtil.fullBright, overlay)
    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, RenderUtil.fullBright, overlay)
    RenderUtil.drawSpriteQuad(pose, vc, sideOn,
      0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, RenderUtil.fullBright, overlay)

    pose.popPose()
  }
}
