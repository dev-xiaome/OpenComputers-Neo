package li.cil.oc.client.gui.traits

import li.cil.oc.api
import li.cil.oc.client.KeyBindings
import li.cil.oc.client.Textures
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.glfw.GLFW

import scala.collection.mutable

/**
 * 把键盘 / 鼠标输入转成文本缓冲区事件的混入（原 1.7.10 的
 * `li.cil.oc.client.gui.traits.InputBuffer`）。
 *
 * 依赖 [[li.cil.oc.api.internal.TextBuffer]] 的输入接口
 * （`keyDown` / `keyUp` / `clipboard`，客户端实现见
 * `common.component.TextBuffer.ClientProxy`）——按 1.7.10 的写法直接调用它们，
 * 网络层（`client.PacketSender`）由缓冲区实现负责，不在这一层。
 *
 * ==1.21.1 迁移要点==
 *  - `initGui()` → `init()`；`onGuiClosed()` → `removed()`；
 *  - `doesGuiPauseGame` → [[isPauseScreen]]；
 *  - `Keyboard.enableRepeatEvents` 已删除：1.21.1 的按键重复由 GLFW 的 REPEAT 事件驱动，
 *    会照常回调 `keyPressed` / `charTyped`，不需要显式开启；
 *  - `handleKeyboardInput()`（读 LWJGL 的全局按键事件）已删除，改成 1.21.1 的回调式 API
 *    [[keyPressed]] / [[charTyped]] / [[keyReleased]]：
 *    GLFW 把「按键」与「字符」拆成了两个回调，因此这里先记下按下的键码，
 *    等 `charTyped` 把字符送来后再合并成一次 `keyDown(char, code)`；
 *    不产生字符的按键（方向键、功能键等）由 [[flushPendingKey]] 在下一帧补发。
 *  - `GuiScreen.getClipboardString` → `Minecraft#keyboardHandler#getClipboard`。
 *  - NEI（`NEI.isInputFocused`）联动整体删除，见下面的 TODO。
 */
trait InputBuffer extends DisplayBuffer {
  protected def buffer: api.internal.TextBuffer

  override protected def bufferColumns: Int = if (buffer == null) 0 else buffer.getViewportWidth

  override protected def bufferRows: Int = if (buffer == null) 0 else buffer.getViewportHeight

  protected def hasKeyboard: Boolean

  /** 已经按下、还没收到 keyUp 的键：键码 → 该键对应的字符。 */
  private val pressedKeys = mutable.Map.empty[Int, Char]

  private var showKeyboardMissing = 0L

  /** 已经按下、正等待 `charTyped` 送来字符的键码（-1 表示没有）。 */
  private var pendingKeyCode = -1

  override def isPauseScreen: Boolean = false

  override protected def init(): Unit = {
    super.init()
    // 1.21.1 不再需要 Keyboard.enableRepeatEvents(true)（见类注释）。
  }

  override protected def drawBufferLayer(guiGraphics: GuiGraphics): Unit = {
    super.drawBufferLayer(guiGraphics)

    if (buffer != null && System.currentTimeMillis() - showKeyboardMissing < 1000) {
      // 原实现用 Tessellator 画 16x16 的四边形；这里等价于把整张贴图铺到 16x16。
      val x = bufferX + buffer.renderWidth - 16
      val y = bufferY + buffer.renderHeight - 16
      guiGraphics.blit(Textures.guiKeyboardMissing, x, y, 0f, 0f, 16, 16, 16, 16)

      RenderState.checkError(getClass.getName + ".drawBufferLayer: keyboard icon")
    }
  }

  override def removed(): Unit = {
    super.removed()
    if (buffer != null) {
      for ((code, char) <- pressedKeys) {
        buffer.keyUp(char, code, null)
      }
      pressedKeys.clear()
    }
    pendingKeyCode = -1
  }

  override def keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = {
    if (super.keyPressed(keyCode, scanCode, modifiers)) return true

    // TODO(integration.NEI): 1.7.10 在这里判断「NEI 的搜索框是否获得焦点」，
    //   是的话就把按键让给 NEI。NEI 没有 1.21.1 版本，这段联动整体删除。

    if (buffer != null && keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_F11) {
      if (hasKeyboard) {
        // 上一次按键如果一直没等到 charTyped（不产生字符的键），先补发掉。
        flushPendingKey()
        pendingKeyCode = keyCode

        if (KeyBindings.isPastingClipboard) {
          buffer.clipboard(Minecraft.getInstance.keyboardHandler.getClipboard, null)
        }
        true
      }
      else {
        showKeyboardMissing = System.currentTimeMillis()
        true
      }
    }
    else false
  }

  override def charTyped(codePoint: Char, modifiers: Int): Boolean = {
    if (super.charTyped(codePoint, modifiers)) return true

    if (buffer != null && hasKeyboard && pendingKeyCode >= 0) {
      val code = pendingKeyCode
      pendingKeyCode = -1
      sendKeyDown(codePoint, code)
      true
    }
    else false
  }

  override def keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = {
    if (super.keyReleased(keyCode, scanCode, modifiers)) return true

    // 按了又马上松开（没等到 charTyped）的键，要先补发按下事件再发抬起事件。
    if (pendingKeyCode == keyCode) flushPendingKey()

    if (buffer != null) pressedKeys.remove(keyCode) match {
      case Some(char) =>
        buffer.keyUp(char, keyCode, null)
        true
      case _ => false // Wasn't pressed while viewing the screen.
    }
    else false
  }

  override def mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    val handled = super.mouseClicked(mouseX, mouseY, button)
    val isMiddleMouseButton = button == 2
    val isBoundMouseButton = KeyBindings.isPastingClipboard
    if (buffer != null && (isMiddleMouseButton || isBoundMouseButton)) {
      if (hasKeyboard) {
        buffer.clipboard(Minecraft.getInstance.keyboardHandler.getClipboard, null)
      }
      else {
        showKeyboardMissing = System.currentTimeMillis()
      }
    }
    handled
  }

  /**
   * 补发「按下但没有字符」的按键事件。
   *
   * 由 [[keyPressed]] / [[keyReleased]] 顺带触发；界面如果希望更快（同一帧内）补发，
   * 可以在自己的 `render` 开头调用它。
   */
  protected def flushPendingKey(): Unit = {
    if (pendingKeyCode >= 0) {
      val code = pendingKeyCode
      pendingKeyCode = -1
      sendKeyDown('\u0000', code)
    }
  }

  private def sendKeyDown(char: Char, code: Int): Unit = {
    if (buffer != null && (!pressedKeys.contains(code) || !ignoreRepeat(char, code))) {
      buffer.keyDown(char, code, null)
      pressedKeys += code -> char
    }
  }

  /** 修饰键的重复按下事件没有意义，原实现同样会过滤掉。 */
  private def ignoreRepeat(char: Char, code: Int): Boolean = {
    code == GLFW.GLFW_KEY_LEFT_CONTROL ||
      code == GLFW.GLFW_KEY_RIGHT_CONTROL ||
      code == GLFW.GLFW_KEY_LEFT_ALT ||
      code == GLFW.GLFW_KEY_RIGHT_ALT ||
      code == GLFW.GLFW_KEY_LEFT_SHIFT ||
      code == GLFW.GLFW_KEY_RIGHT_SHIFT ||
      code == GLFW.GLFW_KEY_LEFT_SUPER ||
      code == GLFW.GLFW_KEY_RIGHT_SUPER
  }
}
