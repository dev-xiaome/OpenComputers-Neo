package li.cil.oc.client.renderer.markdown.segment.render

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.api.manual.ImageRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack

private[markdown] class ItemStackImageRenderer(val stacks: Array[ItemStack]) extends ImageRenderer {
  // How long to show individual stacks, in milliseconds, before switching to the next.
  final val cycleSpeed = 1000

  override def getWidth = 32

  override def getHeight = 32

  override def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    val mc = Minecraft.getInstance()
    val index = ((System.currentTimeMillis() % (cycleSpeed * stacks.length)) / cycleSpeed).toInt
    val stack = stacks(index)

    graphics.pose.pushPose()
    graphics.pose.scale(getWidth / 16.0f, getHeight / 16.0f, getWidth / 16.0f)

    graphics.renderItem(stack, 0, 0)
    graphics.renderItemDecorations(mc.font, stack, 0, 0)

    graphics.pose.popPose()
  }
}