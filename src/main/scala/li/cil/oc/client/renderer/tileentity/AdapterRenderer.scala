package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction

/**
 * 适配器（Adapter）方块实体渲染器。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**（client 包的
 *    `Proxy` 用 `new AdapterRenderer` 注册）。
 *  - 1.7.10 的渲染回调还会把方块坐标当参数传进来，1.21.1 的 `PoseStack` **入场时原点
 *    已经是方块角**，因此原来的 `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 变成
 *    `pose.translate(0.5, 0.5, 0.5)`。
 *  - 原实现靠 `glScaled(1.0025, -1.0025, 1.0025)` 把覆盖层顶出方块表面以避开
 *    z-fighting；这里用 `pose.scale` 保留同样的 1.0025 外扩量。
 *  - `GL11.glPushAttrib/glPopAttrib`、`RenderState.disableLighting/enableLighting`、
 *    `RenderState.makeItBlend` 整体删除：1.21.1 没有固定管线状态要保存，混合与深度写入
 *    改由 `RenderType` 描述；自发光则把 `RenderUtil.fullBright` 写进顶点。
 *  - `Tessellator#startDrawingQuads/addVertexWithUV/draw` 换成一次
 *    `RenderUtil.drawSpriteQuad`（内部是 `PoseStack` 加 `VertexConsumer`）。
 *  - `Textures.Adapter.iconOn`（`IIcon`）换成 `Textures.Block.AdapterOn`（`ResourceLocation`）
 *    加 `RenderUtil.sprite`；精灵可能为 `null`，`drawSpriteQuad` 内部已判空。
 *
 * ==UV 与 RenderType 的取舍==
 *  - 贴图 `adapteron` 是**动画方块贴图**（16x192，6 帧），所以只能走方块图集，
 *    即 `RenderType.cutout()` 加 `TextureAtlasSprite`；不能选 `entityCutout` 这类
 *    指向独立贴图文件的 RenderType（它们要求贴图不在图集里）。
 *  - 该贴图带二值 alpha（透明加不透明），因此选 `cutout()` 而不是 `solid()`：
 *    `solid()` 不做 alpha 测试，透明像素会被渲染成黑块。原实现的
 *    `RenderState.makeItBlend` 只在需要真正半透明时才有意义，这里不需要。
 *  - 整张贴图铺满一个面：1.7.10 的 `icon.getMinU/getMaxU` 只覆盖图集的**当前动画帧**，
 *    1.21.1 的 `TextureAtlasSprite#getU0/getU1` 语义完全一致（也按帧返回），
 *    所以直接用 `drawSpriteQuad` 就是对原效果的直译，不需要手工换算 UV。
 */
class AdapterRenderer extends BlockEntityRenderer[tileentity.Adapter] {

  /** 等价于 1.7.10 的 `GL11.glScaled(1.0025, ...)`。 */
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.Adapter, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    // 原实现的门槛：只要还有任意一个「打开」的侧面就要画覆盖层。
    if (!t.openSides.contains(true)) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val sideActivity = RenderUtil.sprite(Textures.Block.AdapterOn)

    pose.pushPose()
    // 1.7.10 的 `glTranslatef(-0.5, -0.5, -0.5)` 与前面的 +0.5 平移互相抵消，
    // 这里只剩「绕方块中心缩放」，缩放中心通过前后各一次平移实现。
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 四个顶点的顺序逐条对齐原来的 addVertexWithUV 调用顺序；
    // 只画打开的面，关闭的面由方块本体模型负责，覆盖层画上去会穿模。
    if (t.isSideOpen(Direction.DOWN)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.UP)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.NORTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.SOUTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.WEST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, RenderUtil.fullBright, overlay)
    }

    if (t.isSideOpen(Direction.EAST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, RenderUtil.fullBright, overlay)
    }

    pose.popPose()
  }
}
