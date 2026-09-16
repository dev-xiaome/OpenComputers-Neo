package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.common.container
import li.cil.oc.common.container.ComponentSlot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

import scala.jdk.CollectionConverters._

/**
 * 打印机界面（原 1.7.10 的 `li.cil.oc.client.gui.Printer`）。
 *
 * 三个进度条分别表示材料、墨水与打印进度，鼠标悬停在材料 / 墨水条上时显示具体数值。
 *
 * ==1.21.1 迁移要点==
 *  - `drawGuiContainerBackgroundLayer` 拆成
 *    [[CustomGuiContainer.drawSecondaryBackgroundLayer]]（底图 + 刷新进度条数值）+
 *    父类自动调用的 `drawWidgets`（画进度条）+ 父类自动调用的 `drawInventorySlots`。
 *    因此界面**不再**自己调 `drawWidgets()` / `drawInventorySlots()`，否则会画两遍。
 *  - `func_146978_c(x, y, w, h, mouseX, mouseY)`（1.7.10 `GuiScreen` 的
 *    「鼠标是否在矩形内」）→ [[isHovering]]，参数同样是**界面内**坐标。
 *  - `inventoryContainer.getSlot(i).getStack` → `menu.getSlot(i).getItem`。
 */
class Printer(menu: container.Printer, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Printer](menu, playerInventory, title) {

  imageWidth = 176
  imageHeight = 166

  private val materialBar = addWidgetToContainer(new ProgressBar(40, 21) {
    override def width: Int = 62

    override def height: Int = 12

    override def barTexture = Textures.guiPrinterMaterial
  })
  private val inkBar = addWidgetToContainer(new ProgressBar(40, 53) {
    override def width: Int = 62

    override def height: Int = 12

    override def barTexture = Textures.guiPrinterInk
  })
  private val progressBar = addWidgetToContainer(new ProgressBar(105, 20) {
    override def width: Int = 46

    override def height: Int = 46

    override def barTexture = Textures.guiPrinterProgress
  })

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.printer.getInventoryName),
      8, 6, 0x404040, false)

    if (isHovering(materialBar.x, materialBar.y, materialBar.width, materialBar.height, mouseX, mouseY)) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.add(menu.amountMaterial + "/" + menu.printer.maxAmountMaterial)
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
    if (isHovering(inkBar.x, inkBar.y, inkBar.width, inkBar.height, mouseX, mouseY)) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.add(menu.amountInk + "/" + menu.printer.maxAmountInk)
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiPrinter, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    materialBar.level = menu.amountMaterial / menu.printer.maxAmountMaterial.toDouble
    inkBar.level = menu.amountInk / menu.printer.maxAmountInk.toDouble
    progressBar.level = menu.progress
  }

  /** 打印机不画「槽位不可用」的占位图标（与原实现一致）。 */
  override protected def drawDisabledSlot(guiGraphics: GuiGraphics, slot: ComponentSlot): Unit = {}
}
