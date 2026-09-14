package li.cil.oc.common.component

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.common.tileentity

/**
 * 屏幕组件（对应 1.7.10 的 `common.component.Screen`）。
 *
 * 在 [[TextBuffer]] 之上补充触摸模式相关的两个回调；其余文本缓冲逻辑完全复用。
 *
 * 1.21.1 迁移要点：
 *  - `ServerPacketSender.sendScreenTouchMode` 的入参由「方块实体」改为
 *    「维度 + 坐标」（见 [[ServerPacketSender]]，等 `server.PacketSender` 移植后回归）。
 */
class Screen(val screen: tileentity.Screen) extends TextBuffer(screen) {
  @Callback(direct = true, doc = """function():boolean -- Whether touch mode is inverted (sneak-activate opens GUI, instead of normal activate).""")
  def isTouchModeInverted(computer: Context, args: Arguments): Array[AnyRef] = result(screen.invertTouchMode)

  @Callback(doc = """function(value:boolean):boolean -- Sets whether to invert touch mode (sneak-activate opens GUI, instead of normal activate).""")
  def setTouchModeInverted(computer: Context, args: Arguments): Array[AnyRef] = {
    val newValue = args.checkBoolean(0)
    val oldValue = screen.invertTouchMode
    if (newValue != oldValue) {
      screen.invertTouchMode = newValue
      val pos = screen.getBlockPos
      ServerPacketSender.sendScreenTouchMode(screen.getLevel, pos.getX, pos.getY, pos.getZ, newValue)
    }
    result(oldValue)
  }
}
