package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * 充电器界面（原 1.7.10 的 `li.cil.oc.client.gui.Charger`）。
 *
 * ==1.21.1 迁移要点==
 *  - 屏幕构造器固定为 `(menu, playerInventory, title)`；宿主充电器方块实体由
 *    `MenuTypes` 的客户端工厂从载荷里的方块坐标取回，屏幕通过 `menu.charger` 读回。
 *  - `drawSecondaryForegroundLayer(mouseX, mouseY)` →
 *    [[CustomGuiContainer.drawSecondaryForegroundLayer(GuiGraphics, Int, Int)]]。
 */
class Charger(menu: container.Charger, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Charger](menu, playerInventory, title) {

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.charger.getInventoryName),
      8, 6, 0x404040, false)
  }
}
