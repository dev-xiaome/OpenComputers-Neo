package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

import scala.jdk.CollectionConverters._
/**
 * 无人机界面（原 1.7.10 的 `li.cil.oc.client.gui.Drone`）。
 *
 * 界面构成：左侧一块 20x2 的「状态屏」，右侧 8 格物品栏，底部能量条与电源键。
 *
 * ==1.21.1 迁移要点==
 *  - 屏幕构造器固定为 `(menu, playerInventory, title)`；宿主无人机实体由
 *    `MenuTypes` 的客户端工厂从载荷里的实体 id 取回（`menu.drone`）。
 *  - `drawScreen` → [[render(GuiGraphics, Int, Int, Float)]]；
 *    `initGui()` → `init()`；`buttonList` → [[addButton]]。
 *  - `func_146978_c(...)` → [[isHovering]]；`func_146115_a` → [[ImageButton.hoveredState]]；
 *  - `Tessellator` 画选中框 → 一次 `GuiGraphics#blit`。
 *
 * ==降级说明==
 * 1.7.10 用 `traits.DisplayBuffer` + `renderer.gui.BufferRenderer` +
 * `renderer.font.TextBufferRenderData` 把无人机状态画成真正的点阵字体；
 * 那套文本缓冲区渲染子系统尚未移植，因此这里用一块深色底 + 一行状态文字占位。
 */
class Drone(menu: container.Drone, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Drone](menu, playerInventory, title) {

  imageWidth = 176
  imageHeight = 148

  protected var powerButton: ImageButton = _

  private val bufferX = 9
  private val bufferY = 9
  private val bufferColumns = 20
  private val bufferRows = 2

  private val inventoryX = 97
  private val inventoryY = 7

  private val power: ProgressBar = addWidgetToContainer(new ProgressBar(28, 48))

  private val selectionSize = 20
  private val selectionsStates = 17
  private val selectionStepV = 1 / selectionsStates.toDouble

  private def drone = menu.drone

  override def init(): Unit = {
    super.init()
    powerButton = new ImageButton(0, leftPos + 7, topPos + 45, 18, 18, Textures.guiButtonPower, canToggle = true)
    addButton(powerButton)
    powerButton.actionPerformed = _ => onPowerButton()
  }

  /** 原 `actionPerformed(button)`：0 号是电源键。 */
  protected def onPowerButton(): Unit = {
    ClientPacketSender.sendDronePower(drone, !drone.isRunning)
  }

  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    if (powerButton != null) powerButton.toggled = drone.isRunning
    super.render(guiGraphics, mouseX, mouseY, partialTick)
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiDrone, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    power.level = drone.globalBuffer.toDouble / math.max(drone.globalBufferSize.toDouble, 1.0)
    if (drone.mainInventory.getSlots > 0) {
      drawSelection(guiGraphics)
    }
  }

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    // TODO(client.renderer.gui.BufferRenderer): 见类注释的降级说明。
    drawStatusPreview(guiGraphics)

    if (isHovering(power.x, power.y, power.width, power.height, mouseX, mouseY)) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.add(Localization.Computer.Power + ": " +
        (drone.globalBuffer * 100 / math.max(drone.globalBufferSize, 1)) + "% (" +
        drone.globalBuffer + "/" + drone.globalBufferSize + ")")
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
    if (powerButton != null && powerButton.hoveredState) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.addAll(toJava(
        (if (drone.isRunning) Localization.Computer.TurnOff else Localization.Computer.TurnOn)
          .linesIterator.toSeq))
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
  }

  /** 无人机状态屏的占位绘制；见类注释的降级说明。 */
  private def drawStatusPreview(guiGraphics: GuiGraphics): Unit = {
    val w = bufferColumns * 6
    val h = bufferRows * 9
    guiGraphics.fill(leftPos + bufferX, topPos + bufferY, leftPos + bufferX + w, topPos + bufferY + h, 0xFF101010)
    val status = if (drone.statusText == null || drone.statusText.isEmpty) "Drone" else drone.statusText
    var lineY = topPos + bufferY + 1
    for (line <- status.linesIterator.take(bufferRows)) {
      guiGraphics.drawString(font, line, leftPos + bufferX + 1, lineY, 0xFF33FF33, false)
      lineY += 9
    }
  }

  // No custom slots, we just extend DynamicGuiContainer for the highlighting.
  override protected def drawSlotBackground(guiGraphics: GuiGraphics, x: Int, y: Int): Unit = {}

  /** 当前选中槽位的高亮框（原实现用 `Tessellator` 手写，这里等价于一次 blit）。 */
  private def drawSelection(guiGraphics: GuiGraphics): Unit = {
    val slot = drone.selectedSlot
    if (slot >= 0 && slot < 16) {
      val now = System.currentTimeMillis() / 1000.0
      val offsetV = ((now - now.toInt) * selectionsStates).toInt * selectionStepV
      val x = leftPos + inventoryX - 1 + (slot % 4) * (selectionSize - 2)
      val y = topPos + inventoryY - 1 + (slot / 4) * (selectionSize - 2)
      guiGraphics.blit(Textures.guiRobotSelection, x, y, selectionSize, selectionSize,
        0f, offsetV.toFloat, 1, 1, 1, 1)
    }
  }
}
