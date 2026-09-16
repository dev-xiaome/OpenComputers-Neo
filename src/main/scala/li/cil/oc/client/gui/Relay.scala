package li.cil.oc.client.gui

import java.text.DecimalFormat

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * 中继器界面（原 1.7.10 的 `li.cil.oc.client.gui.Relay`）。
 *
 * ==这个界面的特殊之处：界面右侧有一块「外挂标签页」==
 * 升级槽位画在标准界面矩形（[[CustomGuiContainer.imageWidth]]）之外，
 * 因此 1.7.10 不得不在鼠标交互时**临时**把 `xSize` 撑大，否则原版会把
 * 「落在界面外的槽位」上的点击当成丢弃物品。1.21.1 沿用同一套做法，
 * 只是把 `mouseClicked` / `mouseMovedOrUp` 换成对应的 1.21.1 回调：
 *  - `mouseClicked(mouseX: Int, mouseY: Int, button: Int)` →
 *    [[net.minecraft.client.gui.screens.Screen#mouseClicked(double, double, int)]]；
 *  - `mouseMovedOrUp` 在 1.21.1 里被拆成
 *    `mouseReleased` / `mouseDragged`，两者都要覆盖才能保持原来的手感。
 *
 * ==NEI 联动整体删除==
 * 1.7.10 里本类混入了 `codechicken.nei.api.INEIGuiHandler`
 * （隐藏物品面板被标签页遮住的区域）。NEI 没有 1.21.1 版本，
 * 因此 `modifyVisiblity` / `getItemSpawnSlots` / `getInventoryAreas` /
 * `handleDragNDrop` / `hideItemPanelSlot` 全部删除。
 *
 * ==1.21.1 迁移要点==
 *  - `Tessellator` + `glColor4f` 画标签页底图 → 一次
 *    [[net.minecraft.client.gui.GuiGraphics#blit(ResourceLocation, int, int, float, float, int, int, int, int)]]；
 *  - `drawSecondaryForegroundLayer(mouseX, mouseY)` 增加 `GuiGraphics` 参数；
 *  - `20f / relayDelay` 在这种情况下会得到 `Infinity`，原实现的 `DecimalFormat`
 *    会直接抛异常，这里用 [[transferRate]] 做了保护。
 */
class Relay(menu: container.Relay, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Relay](menu, playerInventory, title) {

  private val format = new DecimalFormat("#.##hz")

  /** 外挂标签页的位置（相对界面左上角）。 */
  private val tabX = imageWidth
  private val tabY = 10
  private val tabWidth = 23
  private val tabHeight = 26

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    super.drawSecondaryBackgroundLayer(guiGraphics)
    // 1.7.10 的 `Tessellator` 手写四边形，这里等价于一次 blit。
    guiGraphics.blit(Textures.guiUpgradeTab, leftPos + tabX, topPos + tabY,
      0f, 0f, tabWidth, tabHeight, tabWidth, tabHeight)
  }

  /**
   * 鼠标交互时把界面宽度临时撑大到「标准界面 + 标签页」。
   *
   * 原版用 `imageWidth` 判定「鼠标是否在界面内」，落在界面外的槽位点击会被
   * 当成丢弃。1.7.10 的做法是临时改 `xSize`，这里原样保留。
   */
  private def withTabWidth[T](body: => T): T = {
    val originalWidth = imageWidth
    try {
      imageWidth = originalWidth + tabWidth
      body
    }
    finally imageWidth = originalWidth
  }

  override def mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean =
    withTabWidth(super.mouseClicked(mouseX, mouseY, button))

  override def mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean =
    withTabWidth(super.mouseReleased(mouseX, mouseY, button))

  override def mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean =
    withTabWidth(super.mouseDragged(mouseX, mouseY, button, dragX, dragY))

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.otherInventory.getInventoryName),
      8, 6, 0x404040, false)

    guiGraphics.drawString(font, Localization.Switch.TransferRate, 14, 20, 0x404040, false)
    guiGraphics.drawString(font, Localization.Switch.PacketsPerCycle, 14, 39, 0x404040, false)
    guiGraphics.drawString(font, Localization.Switch.QueueSize, 14, 58, 0x404040, false)

    guiGraphics.drawString(font, transferRate(format, menu.relayDelay), 108, 20, 0x404040, false)
    guiGraphics.drawString(font,
      menu.packetsPerCycleAvg + " / " + menu.relayAmount,
      108, 39, thresholdBasedColor(menu.packetsPerCycleAvg, math.ceil(menu.relayAmount / 2f).toInt, menu.relayAmount), false)
    guiGraphics.drawString(font,
      menu.queueSize + " / " + menu.maxQueueSize,
      108, 58, thresholdBasedColor(menu.queueSize, menu.maxQueueSize / 2, menu.maxQueueSize), false)
  }
}

object Relay {
  /**
   * 把「多少 tick 中继一次」换算成 Hz 文本。
   *
   * 1.7.10 直接写 `format.format(20f / relayDelay)`；但客户端在收到第一次
   * 自定义数据同步之前 `relayDelay` 是 0，`20f / 0` 是 `Infinity`，
   * `DecimalFormat` 会抛 `NumberFormatException` 把整个界面打崩。
   * 这里在 delay <= 0 时退化成 `0hz`。
   */
  def transferRate(format: DecimalFormat, relayDelay: Int): String =
    if (relayDelay <= 0) format.format(0f) else format.format(20f / relayDelay)

  /** 按阈值给数值上色（原实现里的私有小工具，两个界面共用）。 */
  def thresholdBasedColor(value: Int, yellow: Int, red: Int): Int = {
    if (value < yellow) 0x009900
    else if (value < red) 0x999900
    else 0x990000
  }
}
