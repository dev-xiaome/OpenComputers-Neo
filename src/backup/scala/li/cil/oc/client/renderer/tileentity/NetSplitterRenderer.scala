package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction

/**
 * 网络分离器（NetSplitter）的活动指示灯渲染器。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - `Tessellator` 加 `GL11` 矩阵，改成 `PoseStack` 加 `VertexConsumer`；
 *    每个「敞开」的面叠一张 NetSplitterOn 贴图，几何与 1.7.10 一致。
 *  - 原外层的提前返回条件是 `openSides.contains(!isInverted)`：1.7.10 的 `openSides`
 *    是一个「面标记的集合」，1.21.1 的 `traits.OpenSides#openSides` 是
 *    `Array[Boolean]`（索引即 `Direction#ordinal`），因此改写成「存在任意敞开的面」。
 *    单个面是否绘制仍然走 `isSideOpen`（它已经包含反相器语义）。
 *  - `glScaled(1.0025, -1.0025, 1.0025)` 里的 y 轴取反是为了配合 1.7.10 的贴图方向；
 *    1.21.1 用顶点顺序表达贴图方向，不使用负缩放（负缩放会翻转三角形绕序导致背面剔除）。
 */
class NetSplitterRenderer extends BlockEntityRenderer[tileentity.NetSplitter] {

  override def render(t: tileentity.NetSplitter, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    if (!t.openSides.exists(open => open)) return

    val sprite = RenderUtil.sprite(Textures.Block.NetSplitterOn)
    if (sprite == null) return

    val vc = buffer.getBuffer(RenderType.translucent())
    val l = RenderUtil.fullBright

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(1.0025f, 1.0025f, 1.0025f)
    pose.translate(-0.5, -0.5, -0.5)

    // 顶点顺序均为「从该面外侧看：左下、右下、右上、左上」，因此法线朝外、
    // 贴图正立（RenderUtil.drawSpriteQuad 的 UV 约定：顶点 0、1 取贴图下沿）。
    if (t.isSideOpen(Direction.DOWN)) {
      RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, l, overlay)
    }
    if (t.isSideOpen(Direction.UP)) {
      RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, l, overlay)
    }
    if (t.isSideOpen(Direction.NORTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sprite, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, l, overlay)
    }
    if (t.isSideOpen(Direction.SOUTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, l, overlay)
    }
    if (t.isSideOpen(Direction.WEST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, l, overlay)
    }
    if (t.isSideOpen(Direction.EAST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sprite, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, l, overlay)
    }

    pose.popPose()
  }
}
