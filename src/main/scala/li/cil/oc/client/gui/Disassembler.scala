package li.cil.oc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.common.menu
import net.minecraft.world.entity.player.Inventory
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics

class Disassembler(state: menu.Disassembler, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name) {

  val progress = addCustomWidget(new ProgressBar(18, 65))

  override protected def renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040)
    drawSecondaryForegroundLayer(graphics, mouseX, mouseY)

    for (slot <- 0 until menu.slots.size()) {
      drawSlotHighlight(graphics, menu.getSlot(slot))
    }
  }

  override def renderBg(graphics: GuiGraphics, dt: Float, mouseX: Int, mouseY: Int): Unit = {
    RenderSystem.setShaderColor(1, 1, 1, 1)
    graphics.blit(Textures.GUI.Disassembler, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    progress.level = inventoryContainer.disassemblyProgress / 100.0
    drawWidgets(graphics)
  }
}
