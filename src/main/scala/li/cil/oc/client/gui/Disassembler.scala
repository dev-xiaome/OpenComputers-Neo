package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.common.container
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory
import org.lwjgl.opengl.GL11

class Disassembler(playerInventory: Inventory, val disassembler: tileentity.Disassembler) extends DynamicGuiContainer(new container.Disassembler(playerInventory, disassembler)) {
  val progress = addWidget(new ProgressBar(18, 65))

  override def drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) = {
    fontRendererObj.drawString(
      Localization.localizeImmediately(disassembler.getInventoryName),
      8, 6, 0x404040)
  }

  override def drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int): Unit = {
    GL11.glColor3f(1, 1, 1) // Required under Linux.
    mc.renderEngine.bindTexture(Textures.guiDisassembler)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    progress.level = inventoryContainer.disassemblyProgress / 100.0
    drawWidgets()
  }
}
