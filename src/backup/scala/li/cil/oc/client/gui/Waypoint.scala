package li.cil.oc.client.gui

import li.cil.oc.client.PacketSender
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * 路径点（Waypoint）改名界面
 * （基准：1.20 CE 的 `client/gui/Waypoint.scala`，对应 1.7.10 的 `client.gui.Waypoint`）。
 *
 * 这是 1.7.10 里那种「直接在世界上弹一条输入框」的极简界面：一张 176x24 的底图，
 * 中间一个无边框输入框，回车提交标签、右键 / Esc 关闭。
 * 玩家走远（距离平方 > 64，即超过 8 格）时自动关闭，避免留下一个悬空的编辑框。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - `GuiScreen` 换成 [[traits.Window]]（内部已经是 `Screen`），并直接复用它的
 *    `guiLeft` / `guiTop` / `xSize` / `ySize`，不再自己算 `ScaledResolution`
 *    （旧版的居中算法在有 GUI 缩放时与 `Screen` 的结果完全一致）；
 *  - `GuiTextField` 换成 [[EditBox]]：
 *    `setMaxStringLength` 到 `setMaxLength`、`setEnableBackgroundDrawing(false)`
 *    到 `setBordered(false)`、`setText` / `getText` 到 `setValue` / `getValue`；
 *  - 「回车提交」在 1.7.10 是 `keyTyped` 里判断 `Keyboard.KEY_RETURN`；
 *    1.21.1 的 [[EditBox]] 会把按键先吃掉，因此在构造时匿名覆写
 *    [[EditBox#keyPressed]] 拦截 `GLFW_KEY_ENTER`；
 *  - `updateScreen()` 换成 [[tick]]；
 *  - `Keyboard.enableRepeatEvents` 已删除：1.21.1 的按键重复由 GLFW 的 REPEAT 事件驱动，
 *    会照常回调 `keyPressed` / `charTyped`，不需要显式开关；
 *  - 1.7.10 的手工绑定贴图加 `drawTexturedModalRect` 交给 [[traits.Window#render]]
 *    的 `blit`（底图常量见 [[Textures.guiWaypoint]]）。
 *
 * ==与 1.20 CE 的差异==
 *  1. 底图常量用本项目 [[Textures]] 的扁平命名（`guiWaypoint`），
 *     对应 CE 的 `Textures.GUI.Waypoint`；
 *  2. 输入框由 [[traits.Window]] 统一的居中坐标定位，不再单独维护
 *     `leftPos` / `topPos`；
 *  3. 不覆写 `render` 手动再画一次输入框：1.21.1 里 `addRenderableWidget`
 *     注册的控件已经由 `Screen` 的绘制流程渲染，重复画会画两遍（CE 里那行是冗余的）；
 *  4. 不调用 `EditBox#tick`：1.21.1 的 [[EditBox]] / `AbstractWidget` **没有** `tick()`
 *     这个方法（1.20 里那次调用在 1.21.1 无法编译，且无等价行为需要保留）。
 */
class Waypoint(val waypoint: tileentity.Waypoint) extends traits.Window {
  /** 旧版 `guiSize = new ScaledResolution(mc, 176, 24)` 的结果。 */
  override val windowWidth = 176

  override val windowHeight = 24

  override def backgroundImage = Textures.guiWaypoint

  var textField: EditBox = _

  /**
   * 每 tick 检查玩家是否走远。
   *
   * 旧版是 `updateScreen()` 里的 `mc.thePlayer.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64`
   * （方块中心到玩家的距离平方），1.21.1 对应 `Entity#distanceToSqr(double, double, double)`。
   */
  override def tick(): Unit = {
    super.tick()
    val player = minecraft.player
    if (player != null && player.distanceToSqr(waypoint.x + 0.5, waypoint.y + 0.5, waypoint.z + 0.5) > 64) {
      onClose()
    }
  }

  override protected def init(): Unit = {
    super.init()
    minecraft.mouseHandler.releaseMouse()
    KeyMapping.releaseAll()

    // 坐标与 1.20 CE 逐字相同，只是 `leftPos` / `topPos` 在本项目里叫 `guiLeft` / `guiTop`。
    textField = new EditBox(font, guiLeft + 7, guiTop + 8, 164 - 12, 12, Component.empty()) {
      override def keyPressed(keyCode: Int, scanCode: Int, mods: Int): Boolean = {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
          val label = textField.getValue.take(32)
          if (label != waypoint.label) {
            waypoint.label = label
            PacketSender.sendWaypointLabel(waypoint)
            onClose()
          }
          true
        }
        else super.keyPressed(keyCode, scanCode, mods)
      }
    }
    textField.setMaxLength(32)
    // 旧版 `setEnableBackgroundDrawing(false)`：只留文字，不要输入框的深色底。
    textField.setBordered(false)
    textField.setCanLoseFocus(false)
    textField.setTextColor(0xFFFFFF)
    textField.setValue(waypoint.label)

    addRenderableWidget(textField)
    setFocused(textField)
  }
}
