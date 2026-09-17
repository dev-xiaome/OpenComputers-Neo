package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.DiskDrive
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{LightTexture, MultiBufferSource, RenderType}
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemDisplayContext

/**
 * 软盘驱动器（DiskDrive）方块实体渲染器。
 *
 * 两种情况：
 *  1. 槽位里有软盘时，把盘片当「静止物品」画在驱动器正面内侧；
 *  2. 最近 400ms 内有访问时，在正面叠加一层活动指示灯。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`
 *    （1.21.1 的 `PoseStack` 入场原点已经是方块角）。
 *  - `glRotatef(±90 / 180, 0, 1, 0)` 换成 `pose.mulPose(Axis.YP.rotationDegrees(...))`，
 *    `glRotatef(90, -1, 0, 0)` 换成 `pose.mulPose(Axis.XN.rotationDegrees(90f))`。
 *  - `ItemEntity` 加 `RenderManager.instance.renderEntityWithPosYaw` 加
 *    `RenderItem.renderInFrame` 这套 1.7.10 的「借实体渲染器画物品」手法在 1.21.1 已被移除。
 *    这里改用原版物品展示框同款的公开入口
 *    `Minecraft.getInstance.getItemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, ...)`：
 *    它是**静态绘制**，不需要世界里真的存在实体，等价于原来的
 *    「`entity.hoverStart = 0` 加 `renderInFrame`」。
 *  - `OpenGlHelper.setLightmapTextureCoords(...)` 手工改光照贴图坐标的做法，在 1.21.1 改为
 *    把打包好的光照值直接作为 `combinedLight` 传给 `renderStatic`。原来取的是 `facing` 方向
 *    相邻方块的光照，这里沿用同一位置取 `level.getMaxLocalRawBrightness`
 *    （等价于 1.7.10 的 `getLightBrightnessForSkyBlocks`）。
 *  - `RenderState.disableLighting/makeItBlend/setBlendAlpha`、`glPushAttrib/glPopAttrib`
 *    整体删除；指示灯的自发光由 `RenderUtil.fullBright` 写进顶点。
 *
 * ==UV 与 RenderType 的取舍==
 *  - `diskdrivefrontactivity`（16x16，带 mcmeta）是方块图集里的**动画贴图**且带二值 alpha，
 *    因此用 `RenderType.cutout()`：留在方块图集（能取当前帧的 `TextureAtlasSprite`）
 *    同时做 alpha 测试。不用 `solid()`（透明像素会渲染成黑块），
 *    也不用 `translucent()`（没有真正的半透明像素，反而引入排序与深度写入问题）。
 *  - 1.7.10 该覆盖层用硬编码 UV `(0,0)` 到 `(1,1)`；1.21.1 的 UV 必须是图集绝对坐标，
 *    所以改用精灵自己的 `U0/U1/V0/V1`。两者都是「整张贴图铺满同一个四边形」。
 */
class DiskDriveRenderer extends BlockEntityRenderer[DiskDrive] {

  override def render(t: DiskDrive, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    val level = t.getLevel

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 正面朝向 = 方块朝向的反方向（与 1.7.10 的 yaw 分支一致）。
    t.yaw match {
      case Direction.WEST => pose.mulPose(Axis.YP.rotationDegrees(-90f))
      case Direction.NORTH => pose.mulPose(Axis.YP.rotationDegrees(180f))
      case Direction.EAST => pose.mulPose(Axis.YP.rotationDegrees(90f))
      case _ => // 无偏航（SOUTH）。
    }

    // 盘片：贴在正面内侧（原 `glTranslatef(0, 3.5/16, 9/16)` 加绕 X 轴 -90 度）。
    t.items(0) match {
      case Some(stack) if stack != null && !stack.isEmpty =>
        pose.pushPose()
        pose.translate(0, 3.5 / 16, 9 / 16f)
        pose.mulPose(Axis.XN.rotationDegrees(90f))

        val combinedLight =
          if (level == null) RenderUtil.fullBright
          else {
            val litPos = t.getBlockPos.relative(t.facing)
            LightTexture.pack(level.getMaxLocalRawBrightness(litPos), level.getMaxLocalRawBrightness(litPos))
          }

        val mc = Minecraft.getInstance
        if (mc != null && mc.getItemRenderer != null) {
          mc.getItemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, combinedLight, overlay,
            pose, buffer, level, 0)
        }
        // TODO(渲染): 1.7.10 借 `RenderItem.renderInFrame` 关掉了原版「物品堆叠数量」的渲染。
        // `renderStatic` 走普通物品模型（不画数量数字），因此这里无需额外开关；
        // 若后续发现盘片被画上了数量数字，再改用 `ItemStackRenderState` 的自定义路径。

        pose.popPose()
      case _ =>
    }

    // 正面活动指示灯。
    val hasActivity = level != null && System.currentTimeMillis() - t.lastAccess < 400 &&
      level.getRandom.nextDouble() > 0.1
    if (hasActivity) {
      pose.pushPose()
      pose.translate(-0.5, 0.5, 0.505)
      pose.scale(1f, -1f, 1f)

      val sprite = RenderUtil.sprite(Textures.Block.DiskDriveFrontActivity)
      if (sprite != null) {
        val vc = buffer.getBuffer(RenderType.cutout())
        RenderUtil.drawQuad(pose, vc,
          0, 1, 0,
          1, 1, 0,
          1, 0, 0,
          0, 0, 0,
          sprite.getU0, sprite.getV0, sprite.getU1, sprite.getV1,
          RenderUtil.fullBright, overlay)
      }

      pose.popPose()
    }

    pose.popPose()
  }
}
