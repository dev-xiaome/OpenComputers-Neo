package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.player.Inventory
import net.minecraft.network.chat.Component

import scala.jdk.CollectionConverters._
/**
 * 机箱界面（原 1.7.10 的 `li.cil.oc.client.gui.Case`）。
 *
 * 界面构成：一张底图（[[Textures.guiComputer]]）+ 一个电源按钮。
 * 底图上的槽位由容器（[[li.cil.oc.common.container.Case]]）登记，父类负责画。
 *
 * ==1.21.1 迁移要点==
 *  - 屏幕构造器固定为 `(menu, playerInventory, title)`：宿主（机箱方块实体）不再由屏幕
 *    new，而是由 [[li.cil.oc.common.container.MenuTypes]] 的客户端工厂在重建容器时
 *    从载荷里的方块坐标取回（见 `MenuTypes` 的类注释），屏幕通过
 *    `menu.computer` 读回。
 *  - `initGui()` → `init()`；`buttonList.add(...)` → `addRenderableWidget(...)`。
 *  - `drawScreen(mouseX, mouseY, dt)` → `render(guiGraphics, mouseX, mouseY, partialTick)`：
 *    `AbstractContainerScreen#render` 自己会画背景（[[renderBg]]）与前景（[[renderLabels]]），
 *    所以这里只保留「刷新按钮状态」这一件事。
 *  - `drawTexturedModalRect` + `bindTexture` → `GuiGraphics#blit`（见 `CustomGuiContainer`
 *    提供的 1.7.10 风格包装 [[drawTexturedModalRect]]）。
 *  - `func_146115_a`（按钮是否悬停）→ [[ImageButton.hoveredState]]；
 *    tooltip 由 `copiedDrawHoveringText` 转交原版统一绘制。
 */
class Case(menu: container.Case, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Case](menu, playerInventory, title) {

  protected var powerButton: ImageButton = _

  override def init(): Unit = {
    super.init()
    powerButton = new ImageButton(0, leftPos + 70, topPos + 33, 18, 18, Textures.guiButtonPower, canToggle = true)
    powerButton.toggled = menu.computer.isRunning
    // 1.7.10 是 `add(buttonList, powerButton)`；1.21.1 必须登记为可渲染组件才会被绘制。
    addButton(powerButton)
    powerButton.actionPerformed = _ => onPowerButton()
  }

  /** 原 `actionPerformed(button)`：按下电源键时切换计算机开关机状态。 */
  protected def onPowerButton(): Unit = {
    ClientPacketSender.sendComputerPower(menu.computer, !menu.computer.isRunning)
  }

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.computer.getInventoryName),
      8, 6, 0x404040, false)
    if (powerButton != null && powerButton.hoveredState) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.addAll(toJava(
        (if (menu.computer.isRunning) Localization.Computer.TurnOff else Localization.Computer.TurnOn)
          .linesIterator.toSeq))
      // 1.7.10 这里传的是 `mouseX - guiLeft, mouseY - guiTop`：当时的 tooltip 是在
      // `GL11.glTranslatef(guiLeft, guiTop)` 之后的绘制矩阵里**手绘**的，所以要传界面内坐标。
      // 1.21.1 的 tooltip 由原版统一绘制，坐标语义是**屏幕绝对坐标**
      // （界面平移已由 [[CustomGuiContainer.drawHoveringText]] 内部抵消），
      // 因此这里直接传 `mouseX` / `mouseY`；再减一次 `leftPos` / `topPos` 会让提示框
      // 偏到界面左上方。
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiComputer, leftPos, topPos, 0, 0, imageWidth, imageHeight)
  }

  /**
   * 每帧同步按钮状态。
   *
   * 刻意**不**调用 `super.render` 里那套（父类已经做完了），只刷新按钮：
   * `ImageButton` 的 toggle 状态要跟着服务端同步下来的 `computer.isRunning` 走。
   */
  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    if (powerButton != null) {
      powerButton.toggled = menu.computer.isRunning
    }
    super.render(guiGraphics, mouseX, mouseY, partialTick)
  }
}
