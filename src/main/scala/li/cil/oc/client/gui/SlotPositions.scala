package li.cil.oc.client.gui

import net.minecraft.world.inventory.Slot

/**
 * 写 [[net.minecraft.world.inventory.Slot]] 的界面坐标（1.21.1 下只能靠反射）。
 *
 * ==为什么需要反射==
 * 1.7.10 里槽位坐标是**公开可写**字段：
 * {{{
 *   slot.xDisplayPosition = x
 *   slot.yDisplayPosition = y
 * }}}
 * 1.21.1 把它们改成了 `protected final int x` / `protected final int y`，
 * 而且**没有提供任何 setter**（原版自己只在构造时写一次）。
 *
 * 但 OC 的机器人界面必须靠「移动槽位」来实现「一个 16 格的窗口在 64 格物品栏上滚动」，
 * 机架 / 无人机界面在隐藏槽位时也是同样的手法。因此这是 1.21.1 下唯一可行的做法：
 * 用反射写 `Slot#x` / `Slot#y`。
 *
 * ==性能与安全性==
 *  - `Field` 对象只查找一次并缓存（[[xField]] / [[yField]]）；
 *  - 只在客户端界面里调用，不涉及服务端逻辑；
 *  - 若字段被改名（未来版本），退化为「什么都不做」并静默忽略 ——
 *    界面会显示成未滚动状态，但不会崩。
 */
object SlotPositions {
  private lazy val xField: java.lang.reflect.Field = findField("x")

  private lazy val yField: java.lang.reflect.Field = findField("y")

  private def findField(name: String): java.lang.reflect.Field = try {
    val field = classOf[Slot].getDeclaredField(name)
    field.setAccessible(true)
    field
  }
  catch {
    case _: Throwable => null
  }

  /** 把槽位挪到界面内坐标 `(x, y)`（相对界面左上角）。 */
  def set(slot: Slot, x: Int, y: Int): Unit = {
    if (slot == null) return
    write(xField, slot, x)
    write(yField, slot, y)
  }

  private def write(field: java.lang.reflect.Field, slot: Slot, value: Int): Unit = {
    if (field == null) return
    try field.setInt(slot, value)
    catch {
      case _: Throwable => // 字段不可写时静默忽略，见类注释。
    }
  }
}
