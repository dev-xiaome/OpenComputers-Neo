package li.cil.oc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.client.Textures
import li.cil.oc.common.menu
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics

class Raid(state: menu.Raid, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name) {

  override def renderBg(graphics: GuiGraphics, dt: Float, mouseX: Int, mouseY: Int) = {
    RenderSystem.setShaderColor(1, 1, 1, 1) // Required under Linux.
    graphics.blit(Textures.GUI.Raid, leftPos, topPos, 0, 0, imageWidth, imageHeight)
  }
}
