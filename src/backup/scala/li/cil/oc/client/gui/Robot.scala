package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

import scala.jdk.CollectionConverters._

/**
 * 机器人界面（原 1.7.10 的 `li.cil.oc.client.gui.Robot`）。
 *
 * 界面构成：左侧是机器人自带屏幕的实时预览，右侧是 16 格可见物品栏 + 滚动条，
 * 底部是能量条与电源键。
 *
 * ==1.21.1 迁移要点==
 *  - 屏幕构造器固定为 `(menu, playerInventory, title)`；宿主机器人方块实体由
 *    `MenuTypes` 的客户端工厂从载荷里的方块坐标取回（`menu.robot`）。
 *  - `drawScreen` → [[render(GuiGraphics, Int, Int, Float)]]；
 *    `initGui()` → `init()`；`buttonList` → [[addButton]]。
 *  - `slot.xDisplayPosition` / `yDisplayPosition` → `slot.x` / `slot.y`（1.21.1 的公开字段）。
 *  - `Mouse.getEventDWheel`（LWJGL2 全局状态）→
 *    [[net.minecraft.client.gui.screens.Screen#mouseScrolled(double, double, double, double)]]；
 *    鼠标拖拽由 `mouseMovedOrUp` 拆成 [[mouseReleased]] / [[mouseDragged]]。
 *  - `Tessellator` 画选中框 → 一次 `GuiGraphics#blit`（图标动画的 V 偏移继续按时间算）。
 *
 * ==降级说明==
 * 机器人自带屏幕的**文本内容**依赖 `renderer.gui.BufferRenderer` 与
 * `renderer.font.*`（文本缓冲区渲染子系统，尚未移植），因此这里只画出屏幕底板、
 * 并用原版字体打印一行状态文字；等渲染子系统移植完成后，把 [[drawBufferPreview]]
 * 换成 `BufferRenderer.drawText(menu.robot ...)` 即可。
 */
class Robot(menu: container.Robot, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Robot](menu, playerInventory, title) {

  private val withScreenHeight = 256
  private val noScreenHeight = 108

  private val deltaY = if (hasScreen) 0 else withScreenHeight - noScreenHeight

  imageWidth = 256
  imageHeight = 256 - deltaY

  protected var powerButton: ImageButton = _

  protected var scrollButton: ImageButton = _

  // Scroll offset for robot inventory.
  private var inventoryOffset = 0
  private var draggingScrollBar = false

  private def robot = menu.robot

  private def canScroll: Boolean = robot.inventorySize > 16

  private def maxOffset: Int = robot.inventorySize / 4 - 4

  private val slotSize = 18

  private val maxBufferWidth = 240.0
  private val maxBufferHeight = 140.0

  private def bufferRenderWidth = math.min(maxBufferWidth, Settings.screenResolutionsByTier(0)._1 * 6.0)

  private def bufferRenderHeight = math.min(maxBufferHeight, Settings.screenResolutionsByTier(0)._2 * 9.0)

  private val bufferX = (8 + (maxBufferWidth - bufferRenderWidth) / 2).toInt

  private val bufferY = (8 + (maxBufferHeight - bufferRenderHeight) / 2).toInt

  private val inventoryX = 169
  private val inventoryY = 155 - deltaY

  private val scrollX = inventoryX + slotSize * 4 + 2
  private val scrollY = inventoryY
  private val scrollWidth = 8
  private val scrollHeight = 94

  private val power: ProgressBar = addWidgetToContainer(new ProgressBar(26, 156 - deltaY))

  /** 机器人是否装了屏幕（1.7.10 用 `componentEnvironments` 里有没有 `TextBuffer` 判断）。 */
  private def hasScreen: Boolean = menu.hasScreen

  private val selectionSize = 20
  private val selectionsStates = 17
  private val selectionStepV = 1 / selectionsStates.toDouble

  override def init(): Unit = {
    super.init()
    powerButton = new ImageButton(0, leftPos + 5, topPos + 153 - deltaY, 18, 18, Textures.guiButtonPower, canToggle = true)
    scrollButton = new ImageButton(1, leftPos + scrollX + 1, topPos + scrollY + 1, 6, 13, Textures.guiButtonScroll)
    addButton(powerButton)
    addButton(scrollButton)
    powerButton.actionPerformed = _ => onPowerButton()
    scrollButton.actionPerformed = _ => ()
  }

  /** 原 `actionPerformed(button)`：0 号是电源键。 */
  protected def onPowerButton(): Unit = {
    ClientPacketSender.sendComputerPower(robot, !robot.isRunning)
  }

  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    if (powerButton != null) powerButton.toggled = robot.isRunning
    if (scrollButton != null) {
      scrollButton.enabled = canScroll
      scrollButton.hoverOverride = draggingScrollBar
    }
    if (robot.inventorySize < 16 + inventoryOffset * 4) {
      scrollTo(0)
    }
    super.render(guiGraphics, mouseX, mouseY, partialTick)
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    val texture =
      if (hasScreen) Textures.guiRobot
      else Textures.guiRobotNoScreen
    guiGraphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    power.level = robot.globalBuffer / math.max(robot.globalBufferSize, 1.0)
    if (robot.inventorySize > 0) {
      drawSelection(guiGraphics)
    }
  }

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    if (hasScreen) {
      drawBufferPreview(guiGraphics)
    }
    if (isHovering(power.x, power.y, power.width, power.height, mouseX, mouseY)) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.add(Localization.Computer.Power + ": " +
        ((robot.globalBuffer / math.max(robot.globalBufferSize, 1.0)) * 100).toInt + "% (" +
        robot.globalBuffer.toInt + "/" + robot.globalBufferSize.toInt + ")")
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
    if (powerButton != null && powerButton.hoveredState) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.addAll(toJava(
        (if (robot.isRunning) Localization.Computer.TurnOff else Localization.Computer.TurnOn)
          .linesIterator.toSeq))
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
  }

  /**
   * 机器人自带屏幕的预览。
   *
   * TODO(client.renderer.gui.BufferRenderer): 1.7.10 走 `BufferRenderer.drawBackground()`
   *   + `BufferRenderer.drawText(buffer)`（文本缓冲区渲染子系统），该子系统尚未移植，
   *   这里先画一块深色底 + 一行状态文字占位。
   */
  private def drawBufferPreview(guiGraphics: GuiGraphics): Unit = {
    val w = bufferRenderWidth.toInt
    val h = bufferRenderHeight.toInt
    guiGraphics.fill(leftPos + bufferX, topPos + bufferY, leftPos + bufferX + w, topPos + bufferY + h, 0xFF101010)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(robot.getInventoryName),
      leftPos + bufferX + 4, topPos + bufferY + 4, 0xFF33FF33, false)
  }

  // No custom slots, we just extend DynamicGuiContainer for the highlighting.
  override protected def drawSlotBackground(guiGraphics: GuiGraphics, x: Int, y: Int): Unit = {}

  override def mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    val handled = super.mouseClicked(mouseX, mouseY, button)
    if (canScroll && button == 0 && isCoordinateOverScrollBar((mouseX - leftPos).toInt, (mouseY - topPos).toInt)) {
      draggingScrollBar = true
      scrollMouse(mouseY.toInt)
      true
    }
    else handled
  }

  override def mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    if (button == 0) draggingScrollBar = false
    super.mouseReleased(mouseX, mouseY, button)
  }

  override def mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean = {
    if (draggingScrollBar) scrollMouse(mouseY.toInt)
    super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
  }

  /** 原 `handleMouseInput` 的滚轮分支；1.21.1 的入口是 [[mouseScrolled]]。 */
  override def mouseScrolled(mouseX: Double, mouseY: Double, scrollDeltaX: Double, scrollDeltaY: Double): Boolean = {
    val localX = (mouseX - leftPos).toInt
    val localY = (mouseY - topPos).toInt
    if (isCoordinateOverInventory(localX, localY) || isCoordinateOverScrollBar(localX, localY)) {
      if (scrollDeltaY < 0) scrollDown() else if (scrollDeltaY > 0) scrollUp()
      true
    }
    else super.mouseScrolled(mouseX, mouseY, scrollDeltaX, scrollDeltaY)
  }

  private def isCoordinateOverInventory(x: Int, y: Int): Boolean =
    x >= inventoryX && x < inventoryX + slotSize * 4 &&
      y >= inventoryY && y < inventoryY + slotSize * 4

  private def isCoordinateOverScrollBar(x: Int, y: Int): Boolean =
    x > scrollX && x < scrollX + scrollWidth &&
      y >= scrollY && y < scrollY + scrollHeight

  private def scrollUp(): Unit = scrollTo(inventoryOffset - 1)

  private def scrollDown(): Unit = scrollTo(inventoryOffset + 1)

  private def scrollMouse(mouseY: Int): Unit =
    scrollTo(math.round((mouseY - topPos - scrollY + 1 - 6.5) * maxOffset / (scrollHeight - 13.0)).toInt)

  /**
   * 把 16 格可见窗口挪到第 `row` 行。
   *
   * [[li.cil.oc.common.container.Robot]] 为「可见的 16 格」登记了真实坐标，
   * 为剩下的 48 个格子登记了 `-10000`（也就是「藏起来」），因此这里只需要重写前
   * 4 行（索引 4..67）的坐标。
   *
   * ==为什么要用反射写槽位坐标==
   * 1.7.10 的 `Slot#xDisplayPosition` / `yDisplayPosition` 是公开可写字段；
   * 1.21.1 把它们改成了 `protected final int x` / `y`（没有 setter），
   * 而 OC 的机器人界面必须靠移动槽位来实现「滚动 64 格物品栏」。
   * 因此这里通过 [[SlotPositions]] 用反射写入 —— 这是 1.21.1 下唯一可行的做法。
   */
  private def scrollTo(row: Int): Unit = {
    inventoryOffset = math.max(0, math.min(math.max(maxOffset, 0), row))
    for (index <- 4 until 68) {
      val slot = menu.getSlot(index)
      val displayIndex = index - inventoryOffset * 4 - 4
      if (displayIndex >= 0 && displayIndex < 16) {
        SlotPositions.set(slot, 1 + inventoryX + (displayIndex % 4) * slotSize, 1 + inventoryY + (displayIndex / 4) * slotSize)
      }
      else {
        // Hide the rest!
        SlotPositions.set(slot, -10000, -10000)
      }
    }
    val yMin = topPos + scrollY + 1
    if (scrollButton != null) {
      if (maxOffset > 0) {
        scrollButton.yPosition = yMin + (scrollHeight - 15) * inventoryOffset / maxOffset
      }
      else {
        scrollButton.yPosition = yMin
      }
    }
  }

  /** 当前选中槽位的高亮框（原实现用 `Tessellator` 手写，这里等价于一次 blit）。 */
  private def drawSelection(guiGraphics: GuiGraphics): Unit = {
    val slot = robot.selectedSlot - inventoryOffset * 4
    if (slot >= 0 && slot < 16) {
      val now = System.currentTimeMillis() / 1000.0
      // 帧号 0..16（原实现 `((now - now.toInt) * selectionsStates).toInt`）。
      val frame = ((now - now.toInt) * selectionsStates).toInt
      val x = leftPos + inventoryX - 1 + (slot % 4) * (selectionSize - 2)
      val y = topPos + inventoryY - 1 + (slot / 4) * (selectionSize - 2)
      // ==UV 必须按「一帧 = 贴图高度的 1/17」取（高亮框糊成一团的修复点）==
      // 1.7.10 是手写顶点：
      //   `uv(0, offsetV)` 到 `uv(1, offsetV + selectionStepV)`，
      //   其中 `selectionStepV = 1 / 17`（robot_selection.png 是 20x340，纵向 17 帧动画）。
      // 1.21.1 的 `GuiGraphics#blit` 里 uWidth / vHeight / texWidth / texHeight 全是 Int，
      // 表达不了 1/17，所以这里把「贴图高度 17、每次只取 1 像素高」代进去：
      //   v 从 vOffset 除 texHeight 到 (vOffset + vHeight) 除 texHeight，
      //   也就是 frame 除 17 到 (frame + 1) 除 17，正好一帧。
      // 早期移植版把 texHeight 与 vHeight 都写成 1，v 方向于是变成
      // `frame` 到 `frame + 1`（取满整张贴图），17 帧全部叠进 20x20 里。
      guiGraphics.blit(Textures.guiRobotSelection, x, y, selectionSize, selectionSize,
        0f, frame.toFloat, 1, 1, 1, selectionsStates)
    }
  }
}
