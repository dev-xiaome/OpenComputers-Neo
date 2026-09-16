package li.cil.oc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.menu
import net.minecraft.client.Minecraft

import net.minecraft.world.entity.player.Inventory
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import scala.jdk.CollectionConverters._

class Server(state: menu.Server, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name)
  with traits.LockedHotbar[menu.Server] {

  protected var powerButton: ImageButton = _

  override def lockedStack = inventoryContainer.stack

  override def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, dt: Float) = {
    powerButton.visible = !inventoryContainer.isItem
    powerButton.toggled = inventoryContainer.isRunning
    super.render(graphics, mouseX, mouseY, dt)
  }

  override protected def init() = {
    super.init()
    powerButton = new ImageButton(leftPos + 48, topPos + 33, 18, 18, new Button.OnPress {
      override def onPress(b: Button) = if (inventoryContainer.rackSlot >= 0) {
        ClientPacketSender.sendServerPower(inventoryContainer, inventoryContainer.rackSlot, !inventoryContainer.isRunning)
      }
    }, Textures.GUI.ButtonPower, canToggle = true)
    addRenderableWidget(powerButton)
  }

  override def drawSecondaryForegroundLayer(graphics: GuiGraphics, mouseX: Int, mouseY: Int) = {
    super.drawSecondaryForegroundLayer(graphics, mouseX, mouseY)
    if (powerButton.isMouseOver(mouseX, mouseY)) {
      val tooltip = new java.util.ArrayList[Component]
      tooltip.addAll(if (inventoryContainer.isRunning) Localization.Computer.TurnOff.linesIterator.map(Component.literal).toList.asJava else Localization.Computer.TurnOn.linesIterator.map(Component.literal).toList.asJava)
      graphics.renderComponentTooltip(font, tooltip, mouseX - leftPos, mouseY - topPos)
    }
  }

  override def drawSecondaryBackgroundLayer(graphics: GuiGraphics) = {
    RenderSystem.setShaderColor(1, 1, 1, 1.0f)
    graphics.blit(Textures.GUI.Server, leftPos, topPos, 0, 0, imageWidth, imageHeight)
  }
}
