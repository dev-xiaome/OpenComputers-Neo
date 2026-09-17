package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.common.tileentity.Printer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{LightTexture, MultiBufferSource}
import net.minecraft.world.item.ItemDisplayContext

/**
 * 3D 打印机（Printer）方块实体渲染器：把「正在打印的方块」当物品悬在方块中心上方旋转。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`
 *    （1.21.1 的 `PoseStack` 入场原点已经是方块角）。
 *  - `GL11.glRotated((System.currentTimeMillis() % 20000) / 20000.0 * 360, 0, 1, 0)`
 *    换成 `pose.mulPose(Axis.YP.rotationDegrees(...))`：每 20 秒绕 Y 轴转一圈。
 *  - `ItemEntity` 加 `RenderManager.instance.renderEntityWithPosYaw` 加
 *    `RenderItem.renderInFrame` 这套 1.7.10 的「借实体渲染器画物品」手法在 1.21.1 已被移除。
 *    这里改用原版物品展示框同款的公开入口
 *    `Minecraft.getInstance.getItemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, ...)`：
 *    它是**静态绘制**，不需要世界里真的存在实体，等价于原来的
 *    「`entity.hoverStart = 0` 加 `renderInFrame`」。
 *  - 原实现把物品再下移 0.1 格（`renderEntityWithPosYaw(entity, 0, -0.1, 0, 0, 0)`），
 *    这里用 `pose.translate(0, -0.1, 0)` 表达同一偏移。
 *  - `OpenGlHelper.setLightmapTextureCoords(...)` 手工改光照贴图坐标的做法，在 1.21.1 改为
 *    把打包好的光照值直接作为 `combinedLight` 传给 `renderStatic`：原来用方块自身位置取
 *    `getLightBrightnessForSkyBlocks`，这里等价地取
 *    `level.getMaxLocalRawBrightness(t.getBlockPos)`。
 *  - `glPushAttrib/glPopAttrib`、`RenderState.checkError` 删除：没有固定管线状态要保存，
 *    1.21.1 也没有对应的「渲染前检查 GL 错误」入口。
 */
class PrinterRenderer extends BlockEntityRenderer[Printer] {

  override def render(t: Printer, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    // 原门槛：只有配置里确实有「关闭态形状」时才画（否则 createItemStack 没有内容）。
    if (t.data == null || t.data.stateOff.isEmpty) return

    val stack = t.data.createItemStack()
    if (stack == null || stack.isEmpty) return

    val level = t.getLevel

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 每 20000ms 转一整圈（与原实现同一个时间周期）。
    val degrees = (System.currentTimeMillis() % 20000) / 20000.0 * 360.0
    pose.mulPose(Axis.YP.rotationDegrees(degrees.toFloat))

    // 原实现把被打印的方块略微下移，避免与方块本体顶部重叠。
    pose.translate(0, -0.1, 0)

    val combinedLight =
      if (level == null) RenderUtil.fullBright
      else {
        val blockLight = level.getMaxLocalRawBrightness(t.getBlockPos)
        LightTexture.pack(blockLight, blockLight)
      }

    val mc = Minecraft.getInstance
    if (mc != null && mc.getItemRenderer != null) {
      mc.getItemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, combinedLight, overlay,
        pose, buffer, level, 0)
    }
    // TODO(渲染): 1.7.10 借 `RenderItem.renderInFrame` 关掉了原版「物品堆叠数量」的渲染。
    // `renderStatic` 走普通物品模型（不画数量数字），因此这里无需额外开关。

    pose.popPose()
  }
}
