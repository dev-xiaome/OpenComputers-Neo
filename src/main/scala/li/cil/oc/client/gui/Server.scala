package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.container
import li.cil.oc.common.tileentity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

import scala.jdk.CollectionConverters._

/**
 * 服务器界面（原 1.7.10 的 `li.cil.oc.client.gui.Server`）。
 *
 * 服务器同时以两种形态存在：**物品形态**（拿在手里配置）与**机架形态**
 * （插在机架里运行）。界面上唯一的电源按钮只在机架形态下显示。
 *
 * ==1.21.1 迁移要点==
 *  - 屏幕构造器固定为 `(menu, playerInventory, title)`：机架形态所需
 *    「哪台机架 + 第几个槽位」不再由屏幕参数传入，而是由
 *    `MenuTypes` 的客户端工厂在重建容器时从载荷（机架坐标 + 槽位号）取回，
 *    再挂到容器上（见 [[li.cil.oc.common.container.Server#rack]]）。
 *  - `drawScreen(mouseX, mouseY, dt)` → [[render(GuiGraphics, Int, Int, Float)]]：
 *    「物品被从机架里取走就自动关屏」的逻辑保留，但 `displayGuiScreen(null)`
 *    → `Minecraft.getInstance.setScreen(null)`。
 *  - `powerButton.visible = ...` 在 1.21.1 是 `AbstractWidget#visible`，语义一致。
 *  - `inventoryContainer.isRunning` / `isItem` 仍然由容器的自定义数据同步给出
 *    （同步链路见 [[li.cil.oc.common.container.Player.customDataSync]] 的 TODO）。
 */
class Server(menu: container.Server, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Server](menu, playerInventory, title) with traits.LockedHotbar {

  protected var powerButton: ImageButton = _

  override def lockedStack = menu.serverInventory.container

  override def init(): Unit = {
    super.init()
    powerButton = new ImageButton(0, leftPos + 48, topPos + 33, 18, 18, Textures.guiButtonPower, canToggle = true)
    powerButton.visible = !menu.isItem
    addButton(powerButton)
    powerButton.actionPerformed = _ => onPowerButton()
  }

  /** 原 `actionPerformed(button)`：机架形态下切换服务器开关机。 */
  protected def onPowerButton(): Unit = {
    menu.rack match {
      case Some(rack) => ClientPacketSender.sendServerPower(rack, menu.rackSlot, !menu.isRunning)
      case _ =>
    }
  }

  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    // 物品被从机架里取走时自动关屏（原实现写在 drawScreen 里）。
    menu.rack match {
      case Some(rack) if rack.getStackInSlot(menu.rackSlot) != menu.serverInventory.container =>
        Minecraft.getInstance().setScreen(null)
        return
      case _ =>
    }

    if (powerButton != null) {
      powerButton.visible = !menu.isItem
      powerButton.toggled = menu.isRunning
    }
    super.render(guiGraphics, mouseX, mouseY, partialTick)
  }

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.serverInventory.getInventoryName),
      8, 6, 0x404040, false)
    if (powerButton != null && powerButton.hoveredState) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.addAll(asJavaCollection(
        (if (menu.isRunning) Localization.Computer.TurnOff else Localization.Computer.TurnOn)
          .linesIterator.toSeq))
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiServer, leftPos, topPos, 0, 0, imageWidth, imageHeight)
  }
}
