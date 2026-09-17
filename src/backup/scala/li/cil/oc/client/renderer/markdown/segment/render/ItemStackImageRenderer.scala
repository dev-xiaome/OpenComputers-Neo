package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.client.renderer.markdown.RenderContext
import net.minecraft.world.item.ItemStack

/**
 * 把物品图标画进手册页面的图片渲染器（物品/方块图标都走这里）。
 *
 * 1.7.10 用 `RenderItem.getInstance.renderItemAndEffectIntoGUI(...)` + `GL11.glScalef`；
 * 1.21.1 对应 `GuiGraphics#renderItem`，缩放通过 `PoseStack` 完成。
 * `GuiGraphics.renderItem` 内部已经处理了光照与深度测试，不需要再手动开关。
 */
private[markdown] class ItemStackImageRenderer(val stacks: Array[ItemStack]) extends ImageRenderer {
  /** 多张图标轮播时每张显示多久（毫秒）。 */
  final val cycleSpeed = 1000

  override def getWidth: Int = 32

  override def getHeight: Int = 32

  override def render(mouseX: Int, mouseY: Int): Unit = {
    if (stacks == null || stacks.isEmpty) return
    RenderContext.withGraphics { guiGraphics =>
      val index = (System.currentTimeMillis() % (cycleSpeed * stacks.length)).toInt / cycleSpeed
      val stack = stacks(index)
      if (stack != null && !stack.isEmpty) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        // 原实现是 `GL11.glScalef(getWidth / 16, getHeight / 16, getWidth / 16)`：
        // 物品图标本身是 16x16，这里放大到 getWidth x getHeight。
        pose.scale(getWidth / 16f, getHeight / 16f, 1f)
        guiGraphics.renderItem(stack, 0, 0)
        pose.popPose()
      }
    }
  }
}
