package li.cil.oc.client.gui

import li.cil.oc.client.Textures
import li.cil.oc.common.Tier
import li.cil.oc.common.container
import li.cil.oc.common.inventory.DatabaseInventory
import net.minecraft.world.entity.player.Inventory
import org.lwjgl.opengl.GL11

class Database(playerInventory: Inventory, val databaseInventory: DatabaseInventory) extends DynamicGuiContainer(new container.Database(playerInventory, databaseInventory)) with traits.LockedHotbar {
  ySize = 256

  override def lockedStack = databaseInventory.container

  override def drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {}

  override protected def drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int): Unit = {
    GL11.glColor4f(1, 1, 1, 1)
    mc.renderEngine.bindTexture(Textures.guiDatabase)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)

    if (databaseInventory.tier > Tier.One) {
      mc.renderEngine.bindTexture(Textures.guiDatabase1)
      drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    }

    if (databaseInventory.tier > Tier.Two) {
      mc.renderEngine.bindTexture(Textures.guiDatabase2)
      drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    }
  }
}
