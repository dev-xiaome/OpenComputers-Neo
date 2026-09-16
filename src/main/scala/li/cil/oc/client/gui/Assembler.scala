package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.container
import li.cil.oc.common.container.ComponentSlot
import li.cil.oc.common.template.AssemblerTemplates
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot

import scala.jdk.CollectionConverters._

/**
 * 装配机界面（原 1.7.10 的 `li.cil.oc.client.gui.Assembler`）。
 *
 * 界面功能：放一张模板（外壳 / 软盘 / EEPROM …），装配机会按模板校验槽位，
 * 校验通过后「运行」按钮亮起，点击即开始装配，进度条显示装配进度。
 *
 * ==1.21.1 迁移要点==
 *  - `initGui()` → `init()`；`add(buttonList, button)` → `addRenderableWidget`（见 [[addButton]]）；
 *  - `inventorySlots.inventorySlots`（1.7.10 的双层写法）→ `inventorySlots`（本身就是 `IndexedSeq[Slot]`）；
 *  - `slot.getHasStack` → `slot.hasItem`；`slot.getStack` → `slot.getItem`；
 *  - `Component#getUnformattedText` → `Component#getString`；
 *  - `func_146115_a` → [[ImageButton.hoveredState]]；
 *  - `func_146978_c(...)` → [[isHovering]]；
 *  - `drawHoveringText(tooltip: java.util.List[String], ...)`（1.7.10 里被本类**重载**过）
 *    在这里统一走 [[copiedDrawHoveringText]]，避免与 [[CustomGuiContainer.drawHoveringText]]
 *    的 `Component` 版本产生歧义。
 */
class Assembler(menu: container.Assembler, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Assembler](menu, playerInventory, title) {

  imageWidth = 176
  imageHeight = 192

  var info: Option[(Boolean, Component, Array[Component])] = None

  protected var runButton: ImageButton = _

  private val progress: ProgressBar = addWidget(new ProgressBar(28, 92))

  override def init(): Unit = {
    super.init()

    // 槽位内容变化时重新校验模板（原实现在构造器里挂监听）。
    for (slot <- inventorySlots) slot match {
      case component: ComponentSlot => component.changeListener = Option(onSlotChanged)
      case _ =>
    }

    runButton = new ImageButton(0, leftPos + 7, topPos + 89, 18, 18, Textures.guiButtonRun, canToggle = true)
    addButton(runButton)
    runButton.actionPerformed = _ => onRunButton()
    refreshRunButton()
  }

  private def onSlotChanged(slot: Slot): Unit = refreshRunButton()

  private def refreshRunButton(): Unit = {
    if (runButton != null) {
      runButton.enabled = canBuild
      runButton.toggled = !runButton.enabled
    }
    info = validate
  }

  /** 原 `validate`：按模板校验当前槽位。 */
  private def validate: Option[(Boolean, Component, Array[Component])] =
    AssemblerTemplates.select(menu.getSlot(0).getItem).map(_.validate(menu.otherInventory))

  private def canBuild: Boolean = !menu.isAssembling && validate.exists(_._1)

  /** 原 `actionPerformed(button)`：点「运行」时通知服务端开始装配。 */
  protected def onRunButton(): Unit = {
    refreshRunButton()
    if (canBuild) {
      ClientPacketSender.sendRobotAssemblerStart(menu.assembler)
    }
  }

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    if (!menu.isAssembling) {
      val message =
        if (!menu.getSlot(0).hasItem) {
          Localization.Assembler.InsertTemplate
        }
        else info match {
          case Some((_, value, _)) if value != null => value.getString
          case _ if menu.getSlot(0).hasItem => Localization.Assembler.CollectResult
          case _ => ""
        }
      guiGraphics.drawString(font, message, 30, 94, 0x404040, false)
      if (runButton != null && runButton.hoveredState) {
        val tooltip = new java.util.ArrayList[String]()
        tooltip.add(Localization.Assembler.Run)
        info.foreach {
          case (valid, _, warnings) if valid && warnings.length > 0 =>
            tooltip.addAll(asJavaCollection(warnings.map(_.getString).toSeq))
          case _ =>
        }
        copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
      }
    }
    else if (isHovering(progress.x, progress.y, progress.width, progress.height, mouseX, mouseY)) {
      val tooltip = new java.util.ArrayList[String]()
      val timeRemaining = formatTime(menu.assemblyRemainingTime)
      tooltip.add(Localization.Assembler.Progress(menu.assemblyProgress, timeRemaining))
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
  }

  private def formatTime(seconds: Int): String = {
    // Assembly times should not / rarely exceed one hour, so this is good enough.
    if (seconds < 60) f"0:$seconds%02d"
    else f"${seconds / 60}:${seconds % 60}%02d"
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiRobotAssembler, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    if (menu.isAssembling) progress.level = menu.assemblyProgress / 100.0
    else progress.level = 0
  }

  /** 装配机不画「槽位不可用」的占位图标（与原实现一致）。 */
  override protected def drawDisabledSlot(guiGraphics: GuiGraphics, slot: ComponentSlot): Unit = {}
}
