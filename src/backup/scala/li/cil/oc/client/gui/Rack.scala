package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.container
import li.cil.oc.common.tileentity
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

import scala.jdk.CollectionConverters._

/**
 * 机架界面（原 1.7.10 的 `li.cil.oc.client.gui.Rack`）。
 *
 * 这个界面是 OC 里最「自绘」的一个：底图之外还要画
 *  - 4 个插槽 × 4 个可连接点（connectable）的指示图标；
 *  - 5 条总线（bus）上的连接点与连线；
 *  - 一组「总线选择」小按钮（每个可连接点 × 每条总线一个）；
 *  - 右侧的朝向图例；
 *  - 一个「中继模式」开关按钮。
 *
 * ==1.21.1 迁移要点==
 *  - `Tessellator` + `GL11` 手写四边形 → [[net.minecraft.client.gui.GuiGraphics#blit]]：
 *    [[drawRect]] 的语义是「从 256x256 的界面贴图里取一块 (u, v, w, h) 贴到 (x, y)」，
 *    与 `blit(texture, x, y, u, v, w, h, 256, 256)` 完全等价；
 *  - `guiLeft` / `guiTop` 的平移由父类在 `renderBg` / `renderLabels` 里做掉，
 *    因此界面内的绘制一律用**相对坐标**（原实现的 `drawRect` 用的就是相对坐标）；
 *  - `Direction.VALID_DIRECTIONS` 在 1.21.1 不存在，改成 `Direction.values`
 *    （顺序同样是 DOWN, UP, NORTH, SOUTH, WEST, EAST，与原版 `VALID_DIRECTIONS` 一致）；
 *  - `rack.getSizeInventory`（`IInventory`）→ `rack.getSlots`（`IItemHandler`）；
 *  - `button.displayString` 仍然可用（[[ImageButton.displayString]]）；
 *  - `button.visible` / `button.id` 由 [[ImageButton]] 保持兼容；
 *  - `func_146115_a` → [[ImageButton.hoveredState]]；
 *  - `mouseX / mouseY` 在 [[CustomGuiContainer.drawSecondaryForegroundLayer]] 里
 *    已经是**界面相对坐标**，所以「鼠标是否在图例区」的判断用 `0 until 36` 之类的相对值。
 */
class Rack(menu: container.Rack, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Rack](menu, playerInventory, title) {

  imageHeight = 210

  private final val busMasterBlankUVs = (195, 14, 3, 5)
  private final val busMasterPresentUVs = (194, 20, 5, 5)
  private final val busSlaveBlankUVs = (195, 1, 3, 4)
  private final val busSlavePresentUVs = (194, 6, 5, 4)

  private final val connectorMasterUVs = (194, 26, 1, 3)
  private final val connectorSlaveUVs = (194, 11, 1, 2)

  private final val hoverMasterSize = (3, 3)
  private final val hoverSlaveSize = (3, 2)

  private final val wireMasterUVs = Array(
    (186, 16, 6, 3),
    (186, 20, 6, 3),
    (186, 24, 6, 3),
    (186, 28, 6, 3),
    (186, 32, 6, 3)
  )
  private final val wireSlaveUVs = Array(
    (186, 1, 6, 2),
    (186, 4, 6, 2),
    (186, 7, 6, 2),
    (186, 10, 6, 2),
    (186, 13, 6, 2)
  )

  private final val busStart = Array(
    (45, 22),
    (56, 22),
    (67, 22),
    (78, 22),
    (89, 22)
  )

  private final val busGap = 3

  private final val connectorStart = Array(
    (37, 23),
    (37, 43),
    (37, 63),
    (37, 83)
  )

  private final val connectorGap = 2

  private final val relayModeUVs = (195, 30, 4, 2)

  private final val wireRelay = Array(
    (50, 104),
    (61, 104),
    (72, 104),
    (83, 104)
  )

  /** 界面贴图（与原 `bindTexture(Textures.guiRack)` 一致）。 */
  override protected def texture: ResourceLocation = Textures.guiRack

  private final val busToSide = Direction.values().filter(_ != Direction.SOUTH)
  private final val sideToBus = busToSide.zipWithIndex.toMap

  /** 机架本体（原实现由构造参数传入；1.21.1 从容器上取，见 `MenuTypes` 的客户端工厂）。 */
  private def rack: tileentity.Rack = menu.rack

  private def mountableCount: Int = if (rack == null) 0 else rack.getSlots

  var relayButton: ImageButton = _

  // bus -> mountable -> connectable
  var wireButtons: Array[Array[Array[ImageButton]]] =
    Array.fill(5)(Array.fill(4)(Array.fill(5)(null: ImageButton)))

  def sideName(side: Direction): String = side match {
    case Direction.UP => Localization.Rack.Top
    case Direction.DOWN => Localization.Rack.Bottom
    case Direction.WEST => Localization.Rack.Right
    case Direction.EAST => Localization.Rack.Left
    case Direction.NORTH => Localization.Rack.Back
    case _ => Localization.Rack.None
  }

  def encodeButtonId(mountable: Int, connectable: Int, bus: Int): Int = {
    // +1 to offset for relay button
    1 + mountable * 4 * 5 + connectable * 5 + bus
  }

  def decodeButtonId(buttonId: Int): (Int, Int, Int) = {
    // -1 to offset for relay button
    val bus = (buttonId - 1) % 5
    val connectable = ((buttonId - 1) / 5) % 4
    val mountable = (buttonId - 1) / 5 / 4
    (mountable, connectable, bus)
  }

  override def init(): Unit = {
    super.init()

    relayButton = new ImageButton(0, leftPos + 101, topPos + 96, 65, 18, Textures.guiButtonRelay,
      Localization.Rack.RelayDisabled, textIndent = 18)
    addButton(relayButton)
    relayButton.actionPerformed = button => onButton(button)
    relayButton.displayString = if (rack != null && rack.isRelayEnabled) Localization.Rack.RelayEnabled else Localization.Rack.RelayDisabled

    val (mw, mh) = hoverMasterSize
    val (sw, sh) = hoverSlaveSize
    val (_, _, _, mbh) = busMasterBlankUVs
    val (_, _, _, sbh) = busSlaveBlankUVs
    for (bus <- 0 until 5) {
      for (mountable <- 0 until mountableCount) {
        val offset = mountable * (mbh + sbh * 3 + busGap)
        val (bx, by) = busStart(bus)

        {
          val button = new ImageButton(encodeButtonId(mountable, 0, bus), leftPos + bx, topPos + by + offset + 1, mw, mh)
          addButton(button)
          button.actionPerformed = b => onButton(b)
          wireButtons(bus)(mountable)(0) = button
        }

        for (connectable <- 0 until 3) {
          val button = new ImageButton(encodeButtonId(mountable, connectable + 1, bus),
            leftPos + bx, topPos + by + offset + 1 + mbh + sbh * connectable, sw, sh)
          addButton(button)
          button.actionPerformed = b => onButton(b)
          wireButtons(bus)(mountable)(connectable + 1) = button
        }
      }
    }
  }

  /** 原 `actionPerformed(button)`：0 号是中继开关，其余是「总线选择」按钮。 */
  protected def onButton(button: ImageButton): Unit = {
    if (rack == null) return
    if (button.id == 0) {
      ClientPacketSender.sendRackRelayState(rack, !rack.isRelayEnabled)
    }
    else {
      val (mountable, connectable, bus) = decodeButtonId(button.id)
      if (rack.nodeMapping(mountable)(connectable).contains(busToSide(bus))) {
        ClientPacketSender.sendRackMountableMapping(rack, mountable, connectable, None)
      }
      else {
        ClientPacketSender.sendRackMountableMapping(rack, mountable, connectable, Option(busToSide(bus)))
      }
    }
  }

  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    // 按钮可见性跟着容器同步下来的「节点存在性」走（原实现写在 drawScreen 里）。
    if (rack != null) {
      for (mountable <- 0 until mountableCount) {
        val presence = menu.nodePresence(mountable)
        for (connectable <- 0 until 4) {
          for (bus <- 0 until 5) {
            val button = wireButtons(bus)(mountable)(connectable)
            if (button != null) button.visible = presence(connectable)
          }
        }
      }
      if (relayButton != null) {
        relayButton.displayString = if (rack.isRelayEnabled) Localization.Rack.RelayEnabled else Localization.Rack.RelayDisabled
      }
    }
    super.render(guiGraphics, mouseX, mouseY, partialTick)
  }

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)

    if (rack == null) return

    guiGraphics.drawString(font,
      Localization.localizeImmediately(rack.getInventoryName),
      8, 6, 0x404040, false)

    if (rack.isRelayEnabled) {
      val (left, top, w, h) = relayModeUVs
      for ((x, y) <- wireRelay) {
        drawRect(guiGraphics, x, y, w, h, left, top)
      }
    }

    val (mcx, mcy, mcw, mch) = connectorMasterUVs
    val (mbx, mby, mbw, mbh) = busMasterBlankUVs
    val (mpx, mpy, mpw, mph) = busMasterPresentUVs
    val (scx, scy, scw, sch) = connectorSlaveUVs
    val (sbx, sby, sbw, sbh) = busSlaveBlankUVs
    val (spx, spy, spw, sph) = busSlavePresentUVs
    for (mountable <- 0 until mountableCount) {
      val presence = menu.nodePresence(mountable)

      // Draw connectable indicators next to item slots.
      val (cx, cy) = connectorStart(mountable)
      if (presence(0)) {
        drawRect(guiGraphics, cx, cy, mcw, mch, mcx, mcy)
        rack.nodeMapping(mountable)(0) match {
          case Some(side) =>
            val bus = sideToBus(side)
            val (mwx, mwy, mww, mwh) = wireMasterUVs(bus)
            for (i <- 0 to bus) {
              val xOffset = mcw + i * (mpw + mww)
              drawRect(guiGraphics, cx + xOffset, cy, mww, mwh, mwx, mwy)
            }
          case _ =>
        }
        for (connectable <- 1 until 4) {
          rack.nodeMapping(mountable)(connectable) match {
            case Some(side) =>
              val bus = sideToBus(side)
              val (swx, swy, sww, swh) = wireSlaveUVs(bus)
              val yOffset = (mch + connectorGap) + (sch + connectorGap) * (connectable - 1)
              for (i <- 0 to bus) {
                val xOffset = scw + i * (spw + sww)
                drawRect(guiGraphics, cx + xOffset, cy + yOffset, sww, swh, swx, swy)
              }
            case _ =>
          }
        }
      }
      for (connectable <- 1 until 4) {
        if (presence(connectable)) {
          val yOffset = (mch + connectorGap) + (sch + connectorGap) * (connectable - 1)
          drawRect(guiGraphics, cx, cy + yOffset, scw, sch, scx, scy)
        }
      }

      // Draw connection points on buses.
      val yOffset = mountable * (mbh + sbh * 3 + busGap)
      for (bus <- 0 until 5) {
        val (bx, by) = busStart(bus)
        if (presence(0)) {
          drawRect(guiGraphics, bx - 1, by + yOffset, mpw, mph, mpx, mpy)
        }
        else {
          drawRect(guiGraphics, bx, by + yOffset, mbw, mbh, mbx, mby)
        }
        for (connectable <- 0 until 3) {
          if (presence(connectable + 1)) {
            drawRect(guiGraphics, bx - 1, by + yOffset + mph + sph * connectable, spw, sph, spx, spy)
          }
          else {
            drawRect(guiGraphics, bx, by + yOffset + mbh + sbh * connectable, sbw, sbh, sbx, sby)
          }
        }
      }
    }

    for (bus <- 0 until 5) {
      val x = 122
      val y = 20 + bus * 11

      guiGraphics.drawString(font,
        Localization.localizeImmediately(sideName(busToSide(bus))),
        x, y, 0x404040, false)
    }

    // `mouseX` / `mouseY` 已经是界面相对坐标（见类注释）。
    if (mouseX >= 122 && mouseY >= 20 && mouseX < 158 && mouseY < 20 + 5 * 11) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.addAll(toJava(Localization.Rack.OrientationTooltip.linesIterator.toSeq))
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }

    if (relayButton != null && relayButton.hoveredState) {
      val tooltip = new java.util.ArrayList[String]()
      tooltip.addAll(toJava(Localization.Rack.RelayModeTooltip.linesIterator.toSeq))
      copiedDrawHoveringText(tooltip, mouseX, mouseY, font)
    }
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiRack, leftPos, topPos, 0, 0, imageWidth, imageHeight)
  }

  /**
   * 从界面贴图里取一块 `(u, v, w, h)` 贴到界面内 `(x, y)`。
   *
   * 原实现是 `Tessellator` + 手工 UV（按 256x256 归一化），1.21.1 等价于一次 blit。
   */
  private def drawRect(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, u: Int, v: Int): Unit =
    guiGraphics.blit(Textures.guiRack, x, y, u.toFloat, v.toFloat, w, h, 256, 256)
}
