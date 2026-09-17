package li.cil.oc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.menu

import scala.collection.JavaConverters.asJavaCollection
import scala.collection.convert.ImplicitConversionsToJava._
import net.minecraft.world.entity.player.Inventory
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.components.Button
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics

import scala.collection.IterableOnce.iterableOnceExtensionMethods

class Case(state: menu.Case, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name) {

  protected var powerButton: ImageButton = _

  override def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, dt: Float): Unit = {
    powerButton.toggled = inventoryContainer.isRunning
    super.render(graphics, mouseX, mouseY, dt)
  }

  override protected def init(): Unit = {
    super.init()
    powerButton = new ImageButton(leftPos + 70, topPos + 33, 18, 18, (_: Button) => ClientPacketSender.sendComputerPower(inventoryContainer, !inventoryContainer.isRunning), Textures.GUI.ButtonPower, canToggle = true)
    addRenderableWidget(powerButton)
  }

  override protected def drawSecondaryForegroundLayer(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(graphics, mouseX, mouseY)
    if (powerButton.isMouseOver(mouseX, mouseY)) {
      val tooltip = new java.util.ArrayList[Component]
      tooltip.addAll(asJavaCollection(
        (if (inventoryContainer.isRunning) Localization.Computer.TurnOff
        else Localization.Computer.TurnOn)
          .linesIterator
          .map(Component.literal)
          .toIterable
      ))
      graphics.renderComponentTooltip(font, tooltip, mouseX - leftPos, mouseY - topPos)
    }
  }

  override def drawSecondaryBackgroundLayer(graphics: GuiGraphics): Unit = {
    RenderSystem.setShaderColor(1, 1, 1, 1)
    graphics.blit(Textures.GUI.Computer, leftPos, topPos, 0, 0, imageWidth, imageHeight)
  }
}
