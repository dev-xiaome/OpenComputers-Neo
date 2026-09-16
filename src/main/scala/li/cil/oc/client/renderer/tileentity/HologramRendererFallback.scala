package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Hologram
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}

/**
 * 全息投影仪的降级渲染器。
 *
 * 1.7.10 里，当显卡不支持 OpenGL 1.5（没有 VBO）或者显存不足时会退回这里，
 * 只显示一行提示文字；1.21.1 的渲染管线只支持 OpenGL 3.2 core，
 * VBO 与着色器是必备能力，不存在这个分支，因此 `client.Proxy` 只注册
 * [[HologramRenderer]]，**不再注册本类**。
 *
 * 保留本类是为了不动 `Proxy` 里已有的结构、并让源码可编译；
 * 为了保证「万一被手动注册也不会崩」，这里只画一个最简单的半透明方块。
 */
class HologramRendererFallback extends BlockEntityRenderer[Hologram] {

  override def render(t: Hologram, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    val sprite = RenderUtil.sprite(Textures.Block.HologramEffect)
    if (sprite == null) return

    val vc = buffer.getBuffer(RenderType.translucent())
    val l = RenderUtil.fullBright

    pose.pushPose()
    // 只画方块自身的立方体（1.7.10 的提示文字渲染已无意义：本类不再被注册）。
    pose.translate(0.5, 0.5, 0.5)
    // 略微放大一点点，避免与方块本体的面 z-fighting。
    pose.scale(1.0025f, 1.0025f, 1.0025f)
    pose.translate(-0.5, -0.5, -0.5)

    // 顶点顺序「从外侧看：左下、右下、右上、左上」，六面法线朝外。
    // 下面（-Y）
    RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, l, overlay)
    // 上面（+Y）
    RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, l, overlay)
    // 北面（-Z）
    RenderUtil.drawSpriteQuad(pose, vc, sprite, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, l, overlay)
    // 南面（+Z）
    RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, l, overlay)
    // 西面（-X）
    RenderUtil.drawSpriteQuad(pose, vc, sprite, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, l, overlay)
    // 东面（+X）
    RenderUtil.drawSpriteQuad(pose, vc, sprite, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, l, overlay)

    pose.popPose()
  }
}

object HologramRendererFallback {
  /**
   * 原 1.7.10 版本显示在方块上方的提示文字。
   *
   * 1.21.1 不再有「不支持 OpenGL 1.5」这条路径，本字段只为保留
   * [[HologramRenderer]] 里可能存在的赋值语句而留（当前已无引用）。
   */
  var text: String = "Requires OpenGL 1.5"
}
