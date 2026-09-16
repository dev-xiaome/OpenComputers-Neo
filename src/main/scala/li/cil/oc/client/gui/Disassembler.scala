package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * 拆卸机界面（原 1.7.10 的 `li.cil.oc.client.gui.Disassembler`）。
 *
 * ==1.21.1 迁移要点==
 *  - `drawSecondaryForegroundLayer(mouseX, mouseY)` 增加 `GuiGraphics` 参数；
 *  - 底图从 `drawGuiContainerBackgroundLayer` 挪到
 *    [[CustomGuiContainer.drawSecondaryBackgroundLayer]]；
 *  - 进度条（[[ProgressBar]]）由父类的 `render` 统一调用 `drawWidgets` 绘制，
 *    界面**不再**自己调 `drawWidgets()`（否则会被画两次）；
 *  - 进度值在背景层里刷新（与 1.7.10 的时机一致：先更新再绘制）。
 */
class Disassembler(menu: container.Disassembler, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Disassembler](menu, playerInventory, title) {

  val progress: ProgressBar = addWidget(new ProgressBar(18, 65))

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.disassembler.getInventoryName),
      8, 6, 0x404040, false)
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiDisassembler, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    progress.level = menu.disassemblyProgress / 100.0
  }
}
