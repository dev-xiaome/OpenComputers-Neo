package li.cil.oc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.common.menu
import li.cil.oc.common.menu.ComponentSlot
import li.cil.oc.util.RenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics

class Printer(state: menu.Printer, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name) {

  imageWidth = 176
  imageHeight = 166

  private val materialBar = addCustomWidget(new ProgressBar(40, 21) {
    override def width = 62

    override def height = 12

    override def barTexture = Textures.GUI.PrinterMaterial
  })

  private val inkBar = addCustomWidget(new ProgressBar(40, 53) {
    override def width = 62

    override def height = 12

    override def barTexture = Textures.GUI.PrinterInk
  })

  private val progressBar = addCustomWidget(new ProgressBar(105, 20) {
    override def width = 46

    override def height = 46

    override def barTexture = Textures.GUI.PrinterProgress
  })

  override def drawSecondaryForegroundLayer(graphics: GuiGraphics, mouseX: Int, mouseY: Int) = {
    super.drawSecondaryForegroundLayer(graphics, mouseX, mouseY)
    RenderState.pushAttrib()
    if (isHovering(materialBar.x, materialBar.y, materialBar.width, materialBar.height, mouseX - leftPos, mouseY - topPos)) {
      val tooltip: java.util.List[Component] = java.util.List.of(Component.literal(inventoryContainer.amountMaterial + "/" + inventoryContainer.maxAmountMaterial))
      graphics.renderComponentTooltip(font, tooltip, mouseX - leftPos, mouseY - topPos)
    }
    if (isHovering(inkBar.x, inkBar.y, inkBar.width, inkBar.height, mouseX - leftPos, mouseY - topPos)) {
      val tooltip: java.util.List[Component] = java.util.List.of(Component.literal(inventoryContainer.amountInk + "/" + inventoryContainer.maxAmountInk))
      graphics.renderComponentTooltip(font, tooltip, mouseX - leftPos, mouseY - topPos)
    }
    RenderState.popAttrib()
  }

  override def renderBg(graphics: GuiGraphics, dt: Float, mouseX: Int, mouseY: Int): Unit = {
    RenderSystem.setShaderColor(1, 1, 1, 1)
    graphics.blit(Textures.GUI.Printer, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    materialBar.level = inventoryContainer.amountMaterial / inventoryContainer.maxAmountMaterial.toDouble
    inkBar.level = inventoryContainer.amountInk / inventoryContainer.maxAmountInk.toDouble
    progressBar.level = inventoryContainer.progress
    drawWidgets(graphics)
    drawInventorySlots(graphics)
  }

  override protected def drawDisabledSlot(graphics: GuiGraphics, slot: ComponentSlot): Unit = {}
}
