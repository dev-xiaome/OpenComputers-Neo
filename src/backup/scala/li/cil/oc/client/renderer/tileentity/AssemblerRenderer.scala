package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Assembler
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}

/**
 * 装配机（Assembler）方块实体渲染器。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`：
 *    1.21.1 的 `PoseStack` 入场原点已经是方块角。
 *  - `GL11.glRotatef(90, 0, 1, 0)`（每画完一面转 90 度）换成
 *    `pose.mulPose(Axis.YP.rotationDegrees(90f))`。
 *  - 原实现「画完就转」的写法需要靠外层矩阵累积同一个 90 度旋转，这里改为在循环内
 *    **每面单独 push/pop**：每轮都从同一个基准姿态出发再旋转 `i * 90` 度，视觉效果一致，
 *    但不依赖「矩阵必须在 pop 前累积」这个隐式约定。
 *  - `RenderState.disableLighting/makeItBlend/setBlendAlpha`、`glPushAttrib/glPopAttrib`
 *    整体删除；自发光改由顶点光照 `RenderUtil.fullBright` 表达。
 *  - `IIcon`（`Textures.Assembler.iconTopOn` 等）换成 `Textures.Block.AssemblerXxx`
 *    加 `RenderUtil.sprite`（可能为 `null`，绘制前统一判空）。
 *
 * ==UV 与 RenderType 的取舍==
 *  - 三张贴图（`assemblertopon` / `assemblersideon` / `assemblersideassembling`）都是
 *    方块图集里的**动画贴图**，所以只能配 `RenderType.cutout()`（沿用方块图集加方块顶点格式），
 *    不能用指向独立贴图文件的 `entityCutout`。
 *  - 三者都带二值 alpha，故用 `cutout()` 而非 `solid()`（后者不做 alpha 测试会出黑块）。
 *  - 顶面与侧面主体是整张贴图铺满 → `drawSpriteQuad`。
 *  - 装配中的内嵌矩形用的是 `icon.getInterpolatedU((0.5 ± indent) * 16)`，即贴图上的
 *    **部分区域**，不能整张贴满，因此走 `RenderUtil.drawQuad` 并把插值结果换成
 *    `sprite.getU(t * 16 / 16)`——1.21.1 的 `TextureAtlasSprite#getU(t)` 参数是
 *    「当前帧内 0 到 1 的比例」，与 1.7.10 的 `getInterpolatedU(像素)` 除以 16 等价。
 */
class AssemblerRenderer extends BlockEntityRenderer[Assembler] {

  override def render(t: Assembler, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val topSprite = RenderUtil.sprite(Textures.Block.AssemblerTopOn)

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 顶面：贴在方块中心上方（局部 y = 0.55），整张贴图铺满。
    RenderUtil.drawSpriteQuad(pose, vc, topSprite,
      -0.5, 0.55, 0.5, 0.5, 0.55, 0.5, 0.5, 0.55, -0.5, -0.5, 0.55, -0.5,
      RenderUtil.fullBright, overlay)

    // 侧面：原实现从「朝 +X」的姿态起，每画完一面绕 Y 轴转 90 度。
    // 注意显式标注 Float：`6 / 16f + 0.005` 里的字面量 0.005 是 Double，
    // 不标注会把整个表达式推成 Double，进而让 `spriteU` 的参数类型不匹配。
    val indent: Float = 6 / 16f + 0.005f
    val assemblingSprite = RenderUtil.sprite(Textures.Block.AssemblerSideAssembling)
    val sideSprite = RenderUtil.sprite(Textures.Block.AssemblerSideOn)

    for (i <- 0 until 4) {
      pose.pushPose()
      pose.mulPose(Axis.YP.rotationDegrees(i * 90f))

      if (t.isAssembling && assemblingSprite != null) {
        // 装配进度条：只占贴图的中间一段（U 从 (0.5 - indent) 到 (0.5 + indent)），
        // V 方向铺满整帧。顶点顺序与原来的 addVertexWithUV 完全一致。
        RenderUtil.drawQuad(pose, vc,
          indent, 0.5, -indent,
          indent, 0.5, indent,
          indent, -0.5, indent,
          indent, -0.5, -indent,
          spriteU(assemblingSprite, 0.5f - indent), spriteV(assemblingSprite, 1f),
          spriteU(assemblingSprite, 0.5f + indent), spriteV(assemblingSprite, 0f),
          RenderUtil.fullBright, overlay)
      }

      // 侧面主体：Z 方向从 -0.5 到 0.5 铺满，贴图整张铺满。
      RenderUtil.drawSpriteQuad(pose, vc, sideSprite,
        0.5005, 0.5, -0.5, 0.5005, 0.5, 0.5, 0.5005, -0.5, 0.5, 0.5005, -0.5, -0.5,
        RenderUtil.fullBright, overlay)

      pose.popPose()
    }

    pose.popPose()
  }

  /** 当前动画帧内按比例取 U（等价于 1.7.10 的 `getInterpolatedU(像素)` 除以 16）。 */
  private def spriteU(sprite: net.minecraft.client.renderer.texture.TextureAtlasSprite, t: Float): Float =
    if (sprite == null) t else sprite.getU(t)

  /** 当前动画帧内按比例取 V。 */
  private def spriteV(sprite: net.minecraft.client.renderer.texture.TextureAtlasSprite, t: Float): Float =
    if (sprite == null) t else sprite.getV(t)
}
