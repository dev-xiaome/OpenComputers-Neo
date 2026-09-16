package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Microcontroller
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation

/**
 * 微控制器（Microcontroller）方块实体渲染器：正面常亮的底板灯，加上运行 / 故障指示灯。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`
 *    （1.21.1 的 `PoseStack` 入场原点已经是方块角）。
 *  - `glRotatef(±90 / 180, 0, 1, 0)` 换成 `pose.mulPose(Axis.YP.rotationDegrees(...))`。
 *  - `RenderState.disableLighting/makeItBlend/setBlendAlpha`、`glPushAttrib/glPopAttrib`
 *    整体删除；自发光改由 `RenderUtil.fullBright` 写进顶点。
 *  - `Tessellator.instance` 加 `bindTexture(ResourceLocation)` 的写法在 1.21.1 不存在：
 *    顶点必须挂到某个 `VertexConsumer` 上，画哪张贴图由 `RenderType` 决定。
 *
 * ==UV 与 RenderType 的取舍==
 *  - 三张贴图（`microcontrollerfrontlight` / `microcontrollerfronton` /
 *    `microcontrollerfronterror`）都是方块图集里的**动画贴图**且带二值 alpha，
 *    因此统一用 `RenderType.cutout()`：既留在方块图集（能用 `TextureAtlasSprite`），
 *    又做 alpha 测试。不用 `solid()`（透明像素会渲染成黑块），也不用 `translucent()`
 *    （没有真正的半透明像素，反而引入排序与深度写入问题）。
 *  - 1.7.10 里正面底板与指示灯用的都是硬编码 UV `(0,0)` 到 `(1,1)`；1.21.1 的 UV 必须是
 *    图集绝对坐标，所以改为取精灵自己的 `U0/U1/V0/V1`。两者都是
 *    「整张贴图铺满同一个四边形」，效果等价。
 */
class MicrocontrollerRenderer extends BlockEntityRenderer[Microcontroller] {

  override def render(t: Microcontroller, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    val vc = buffer.getBuffer(RenderType.cutout())

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 正面朝向 = 方块朝向的反方向（与 1.7.10 的 yaw 分支一致）。
    t.yaw match {
      case Direction.WEST => pose.mulPose(Axis.YP.rotationDegrees(-90f))
      case Direction.NORTH => pose.mulPose(Axis.YP.rotationDegrees(180f))
      case Direction.EAST => pose.mulPose(Axis.YP.rotationDegrees(90f))
      case _ => // 无偏航（SOUTH）。
    }

    // 原 `glTranslated(-0.5, 0.5, 0.505)` 加 `glScalef(1, -1, 1)`：
    // 把原点挪到「正面在 z = 0、上边在 y = 0」的位置，z = 0.505 顶出方块表面避开
    // z-fighting，负 Y 缩放用来抵消原版纹理坐标 V 轴朝下（同 RenderUtil 的约定）。
    pose.translate(-0.5, 0.5, 0.505)
    pose.scale(1f, -1f, 1f)

    // 正面底板（常亮）。
    renderFrontOverlay(pose, vc, Textures.Block.MicrocontrollerFrontLight, overlay)

    if (t.isRunning) {
      renderFrontOverlay(pose, vc, Textures.Block.MicrocontrollerFrontOn, overlay)
    }
    else if (t.hasErrored && RenderUtil.shouldShowErrorLight(t.hashCode)) {
      renderFrontOverlay(pose, vc, Textures.Block.MicrocontrollerFrontError, overlay)
    }

    pose.popPose()
  }

  /** 在当前位置画一整张正面覆盖层（四边形的四个角即当前姿态下的「正面矩形」）。 */
  private def renderFrontOverlay(pose: PoseStack, vc: com.mojang.blaze3d.vertex.VertexConsumer,
                                 texture: ResourceLocation, overlay: Int): Unit = {
    val sprite: TextureAtlasSprite = RenderUtil.sprite(texture)
    if (sprite == null) return
    RenderUtil.drawQuad(pose, vc,
      0, 1, 0,
      1, 1, 0,
      1, 0, 0,
      0, 0, 0,
      sprite.getU0, sprite.getV0, sprite.getU1, sprite.getV1,
      RenderUtil.fullBright, overlay)
  }
}
