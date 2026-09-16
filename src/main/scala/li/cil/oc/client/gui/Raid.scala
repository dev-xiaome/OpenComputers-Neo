package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.player.Inventory

import scala.jdk.CollectionConverters._

/**
 * RAID 界面（原 1.7.10 的 `li.cil.oc.client.gui.Raid`）。
 *
 * ==1.21.1 迁移要点==
 *  - `drawGuiContainerBackgroundLayer(dt, mouseX, mouseY)` →
 *    [[CustomGuiContainer.drawSecondaryBackgroundLayer(GuiGraphics)]]（底图改由它画）；
 *  - `fontRendererObj.drawSplitString(text, x, y, color, wrapWidth)` →
 *    `Font#split(FormattedText, wrapWidth)` + `GuiGraphics#drawString` 逐行绘制。
 *    注意 1.21.1 的换行宽度是相对界面左上角的，而 [[CustomGuiContainer.drawSecondaryForegroundLayer]]
 *    的 pose 已经被父类平移过，因此这里直接用 `imageWidth` 而不是屏幕宽度 `width`。
 */
class Raid(menu: container.Raid, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Raid](menu, playerInventory, title) {

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiRaid, leftPos, topPos, 0, 0, imageWidth, imageHeight)
  }

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.raid.getInventoryName),
      8, 6, 0x404040, false)

    drawSplitString(guiGraphics, Localization.Raid.Warning, 8, 46, 0x404040, imageWidth - 16)
  }

  /** 等价 1.7.10 的 `FontRenderer#drawSplitString`。 */
  private def drawSplitString(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int, wrapWidth: Int): Unit = {
    if (text == null) return
    val lines: java.util.List[FormattedCharSequence] =
      font.split(Component.literal(text), math.max(1, wrapWidth))
    var lineY = y
    for (line <- lines.asScala) {
      guiGraphics.drawString(font, line, x, lineY, color, false)
      lineY += font.lineHeight
    }
  }
}
