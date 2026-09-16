package li.cil.oc.client.gui

import java.text.DecimalFormat
import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.common.menu
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Rect2i
import org.lwjgl.opengl.GL11
import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics

class Relay(state: menu.Relay, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name) {

  private val format = new DecimalFormat("#.##hz")

  val tabPosition = new Rect2i(imageWidth, 10, 23, 26)

  override protected def drawSecondaryBackgroundLayer(graphics: GuiGraphics): Unit = {
    super.drawSecondaryBackgroundLayer(graphics)

    // Tab background.
    RenderSystem.setShaderColor(1, 1, 1, 1)
    RenderSystem.setShaderTexture(0, Textures.GUI.UpgradeTab)
    val stack = graphics.pose
    val x = windowX + tabPosition.getX
    val y = windowY + tabPosition.getY
    val w = tabPosition.getWidth
    val h = tabPosition.getHeight
    val t = Tesselator.getInstance
    val r = t.getBuilder
    r.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
    r.vertex(stack.last.pose, x, y + h, 0).uv(0, 1).endVertex()
    r.vertex(stack.last.pose, x + w, y + h, 0).uv(1, 1).endVertex()
    r.vertex(stack.last.pose, x + w, y, 0).uv(1, 0).endVertex()
    r.vertex(stack.last.pose, x, y, 0).uv(0, 0).endVertex()
    t.end()
  }

  override def mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    // So MC doesn't throw away the item in the upgrade slot when we're trying to pick it up...
    val originalWidth = imageWidth
    try {
      imageWidth += tabPosition.getWidth
      super.mouseClicked(mouseX, mouseY, button)
    }
    finally {
      imageWidth = originalWidth
    }
  }

  override def mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    // So MC doesn't throw away the item in the upgrade slot when we're trying to pick it up...
    val originalWidth = imageWidth
    try {
      imageWidth += tabPosition.getWidth
      super.mouseReleased(mouseX, mouseY, button)
    }
    finally {
      imageWidth = originalWidth
    }
  }

  override def drawSecondaryForegroundLayer(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(graphics, mouseX, mouseY)

    graphics.drawString(font,
      Localization.Switch.TransferRate,
      14, 20, 0x404040)
    graphics.drawString(font,
      Localization.Switch.PacketsPerCycle,
      14, 39, 0x404040)
    graphics.drawString(font,
      Localization.Switch.QueueSize,
      14, 58, 0x404040)

    graphics.drawString(font,
      format.format(20f / inventoryContainer.relayDelay),
      108, 20, 0x404040)
    graphics.drawString(font,
      inventoryContainer.packetsPerCycleAvg + " / " + inventoryContainer.relayAmount,
      108, 39, thresholdBasedColor(inventoryContainer.packetsPerCycleAvg, math.ceil(inventoryContainer.relayAmount / 2f).toInt, inventoryContainer.relayAmount))
    graphics.drawString(font,
      inventoryContainer.queueSize + " / " + inventoryContainer.maxQueueSize,
      108, 58, thresholdBasedColor(inventoryContainer.queueSize, inventoryContainer.maxQueueSize / 2, inventoryContainer.maxQueueSize))
  }

  private def thresholdBasedColor(value: Int, yellow: Int, red: Int) = {
    if (value < yellow) 0x009900
    else if (value < red) 0x999900
    else 0x990000
  }
}
