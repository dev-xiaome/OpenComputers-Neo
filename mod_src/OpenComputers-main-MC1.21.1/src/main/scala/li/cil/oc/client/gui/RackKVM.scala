package li.cil.oc.client.gui

import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.component.{RackKVM => RackKVMComponent}
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class RackKVM(kvm: RackKVMComponent, hasPower: () => Boolean)
  extends Screen(kvm.buffer, true, () => true, hasPower) {

  private var lastBuffer: li.cil.oc.api.internal.TextBuffer = _

  override def buffer: li.cil.oc.api.internal.TextBuffer = kvm.buffer

  override protected def topPadding: Int = 22

  override def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, dt: Float): Unit = {
    if (lastBuffer ne buffer) {
      lastBuffer = buffer
      requestSynchronization(buffer)
    }

    super.render(graphics, mouseX, mouseY, dt)

    val x0 = (width - 66) / 2
    for (index <- 0 until 3) {
      val available = (kvm.serverMask & (1 << index)) != 0
      val selected = kvm.selectedRackSlot == kvm.consoleSlots(index)
      val left = x0 + index * 22
      val background = if (selected && available) 0xCC2C8FBE else if (selected) 0xCC6B4A2D else if (available) 0xCC303030 else 0xCC151515
      val foreground = if (available) 0xFFFFFF else 0x666666
      graphics.fill(left, 4, left + 20, 18, background)
      graphics.drawCenteredString(font, Component.literal((index + 1).toString), left + 10, 7, foreground)
    }
  }

  override def mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseY >= 4 && mouseY < 18) {
      val relativeX = mouseX - (width - 66) / 2
      val index = (relativeX / 22).toInt
      if (relativeX >= 0 && index >= 0 && index < 3 && relativeX - index * 22 < 20) {
        select(index)
        return true
      }
    }
    super.mouseClicked(mouseX, mouseY, button)
  }

  override def keyPressed(keyCode: Int, scanCode: Int, mods: Int): Boolean = {
    if (screens.Screen.hasControlDown() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_3) {
      select(keyCode - GLFW.GLFW_KEY_1)
      return true
    }
    super.keyPressed(keyCode, scanCode, mods)
  }

  private def select(index: Int): Boolean = {
    if ((kvm.serverMask & (1 << index)) == 0) return false
    kvm.rack match {
      case rack: li.cil.oc.common.blockentity.Rack =>
        ClientPacketSender.sendRackKVMSelection(rack, kvm.slot, kvm.consoleSlots(index))
        kvm.bufferAtRackSlot(kvm.consoleSlots(index)).foreach(requestSynchronization)
        true
      case _ => false
    }
  }

  private def requestSynchronization(target: li.cil.oc.api.internal.TextBuffer): Unit = target match {
    case concrete: li.cil.oc.common.component.TextBuffer => concrete.requestSynchronization()
    case _ =>
  }
}
