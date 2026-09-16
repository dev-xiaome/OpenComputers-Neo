package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.common.event.RackMountableRenderHandler
import li.cil.oc.common.tileentity.Rack
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction
import net.neoforged.neoforge.common.NeoForge
import org.joml.Quaternionf

/**
 * 机架渲染器：把「已安装的挂载物（磁盘驱动器 / 服务器 / 终端服务器）」的
 * 动态覆盖层（指示灯、盘片）渲染出来。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - `Tessellator` 加 `GL11` 矩阵，改成 `PoseStack` 加 `VertexConsumer`。
 *  - 1.7.10 在这里直接 post `RackMountableRenderEvent.BlockEntity`，事件处理器用全局
 *    `Tessellator` 画覆盖层。1.21.1 的顶点必须写进 `MultiBufferSource`，而事件本身
 *    （`li.cil.oc.api.event`，已冻结）不携带渲染上下文，因此按
 *    [[RackMountableRenderHandler.setRenderContext]] 的约定，在 post 之前把当前
 *    `(PoseStack, MultiBufferSource)` 交给处理器，post 结束后清除。
 *  - 坐标变换与 1.7.10 完全一致：先平移到方块中心，按 `yaw` 旋转，
 *    再平移到「机架正面的左上角」（`(-0.5, 0.5, 0.505 - 1/16)`），
 *    最后 `scale(1, -1, 1)`。注意这里**必须保留这个 y 轴镜像**：
 *    事件约定里 y 轴朝下、z 轴朝外，`RackMountableRenderHandler` 内部也是用
 *    `scale(1, -1, 1)` 把坐标翻回 y 朝上来渲染盘片物品的。
 *  - 镜像会把三角形的绕序翻转，而事件处理器写覆盖层时用的是
 *    `RenderType.entityCutout`（开启背面剔除），会让覆盖层被剔除。
 *    由于 api 层（冻结）不提供「无剔除」变体，这里用一个 `MultiBufferSource` 装饰器
 *    把机架用到的几张覆盖层贴图的 `entityCutout` 换成 `entityCutoutNoCull`
 *    （见 [[noCullOverrides]]）。物品渲染用的 RenderType 不在映射里，原样透传，
 *    因此盘片物品的绕序（镜像 + 处理器自己的镜像 = 正常）不受影响。
 */
class RackRenderer extends BlockEntityRenderer[Rack] {

  private final val vOffset = 2 / 16f
  private final val vSize = 3 / 16f

  /**
   * 覆盖层贴图的 `entityCutout` 到 `entityCutoutNoCull` 的映射。
   *
   * `RenderType` 把贴图藏在 `protected` 状态位里，无法从实例反查贴图，
   * 因此这里按「机架用到的覆盖层贴图集合」预先建立映射，按值匹配即可。
   * 如果以后 [[RackMountableRenderHandler]] 增加新的覆盖层贴图，
   * 需要把那张贴图也加进这个列表，否则该覆盖层会因背面剔除而不可见。
   * （覆盖层本身仍然是 `RenderType` 决定的半透明 / alpha 裁剪行为，只改了剔除。）
   */
  private lazy val noCullOverrides: Map[RenderType, RenderType] = {
    val overlays = Seq(
      Textures.Block.RackDiskDriveActivity,
      Textures.Block.RackServerOn,
      Textures.Block.RackServerError,
      Textures.Block.RackServerActivity,
      Textures.Block.RackServerNetworkActivity,
      Textures.Block.RackTerminalServerOn,
      Textures.Block.RackTerminalServerPresence)
    overlays.map(texture => RenderType.entityCutout(texture) -> RenderType.entityCutoutNoCull(texture)).toMap
  }

  override def render(t: Rack, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return

    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    t.yaw match {
      case Direction.WEST => rotateY(pose, -90)
      case Direction.NORTH => rotateY(pose, 180)
      case Direction.EAST => rotateY(pose, 90)
      case _ => // 南向即默认朝向，不需要旋转。
    }

    pose.translate(-0.5, 0.5, 0.505 - 1.0 / 16)
    pose.scale(1f, -1f, 1f)

    // 事件处理器（common/event/RackMountableRenderHandler）需要当前渲染上下文。
    val noCullBuffer = new MultiBufferSource {
      override def getBuffer(renderType: RenderType): VertexConsumer =
        buffer.getBuffer(noCullOverrides.getOrElse(renderType, renderType))
    }
    RackMountableRenderHandler.setRenderContext(() => (pose, noCullBuffer))

    try {
      // 与原实现一致：只有装了东西的槽位才通知（注释里说的「手动同步机架物品栏」
      // 在 1.21.1 由方块实体的同步标签负责）。
      for (i <- 0 until t.getSlots) {
        val stack = t.getStackInSlot(i)
        if (stack != null && !stack.isEmpty) {
          val v0 = vOffset + i * vSize
          val v1 = vOffset + (i + 1) * vSize
          val event = new RackMountableRenderEvent.BlockEntity(t, i, t.getMountableData(i), v0, v1)
          NeoForge.EVENT_BUS.post(event)
        }
      }
    }
    finally {
      // 避免处理器继续持有已经失效的缓冲区。
      RackMountableRenderHandler.clearRenderContext()
      pose.popPose()
    }
  }

  private def rotateY(pose: PoseStack, degrees: Float): Unit = {
    val radians = math.toRadians(degrees.toDouble).toFloat
    pose.mulPose(new Quaternionf().rotateAxis(radians, 0f, 1f, 0f))
  }
}
