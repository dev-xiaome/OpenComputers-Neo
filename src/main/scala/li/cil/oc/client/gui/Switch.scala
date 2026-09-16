package li.cil.oc.client.gui

import java.text.DecimalFormat

import li.cil.oc.Localization
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * 交换机界面（原 1.7.10 的 `li.cil.oc.client.gui.Switch`）。
 *
 * 与 [[Relay]] 的差别只在标题文案来源，统计数值完全一样（都从容器的
 * `synchronizedData` 里读）。
 *
 * ==1.21.1 迁移要点==
 *  - `drawSecondaryForegroundLayer(mouseX, mouseY)` 增加 `GuiGraphics` 参数；
 *  - `fontRendererObj.drawString` → `GuiGraphics#drawString(..., shadow = false)`；
 *  - `20f / relayDelay` 在客户端还没收到同步数据时是 `20f / 0 = Infinity`，
 *    原实现的 `DecimalFormat` 会直接抛 `NumberFormatException`，这里加了保护。
 */
class Switch(menu: container.Switch, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Switch](menu, playerInventory, title) {

  private val format = new DecimalFormat("#.##hz")

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.otherInventory.getInventoryName),
      8, 6, 0x404040, false)

    guiGraphics.drawString(font, Localization.Switch.TransferRate, 14, 20, 0x404040, false)
    guiGraphics.drawString(font, Localization.Switch.PacketsPerCycle, 14, 39, 0x404040, false)
    guiGraphics.drawString(font, Localization.Switch.QueueSize, 14, 58, 0x404040, false)

    guiGraphics.drawString(font, Relay.transferRate(format, menu.relayDelay), 108, 20, 0x404040, false)
    guiGraphics.drawString(font,
      menu.packetsPerCycleAvg + " / " + menu.relayAmount,
      108, 39, Relay.thresholdBasedColor(menu.packetsPerCycleAvg, math.ceil(menu.relayAmount / 2f).toInt, menu.relayAmount), false)
    guiGraphics.drawString(font,
      menu.queueSize + " / " + menu.maxQueueSize,
      108, 58, Relay.thresholdBasedColor(menu.queueSize, menu.maxQueueSize / 2, menu.maxQueueSize), false)
  }
}
