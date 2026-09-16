package li.cil.oc.client.gui.traits

import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * 槽位锁定的扩展点。
 *
 * 1.7.10 的 `LockedHotbar` 是 `trait LockedHotbar extends GuiContainer`，直接覆写
 * `handleMouseClick` / `checkHotbarKeys`。1.21.1 不能这么写：
 * `AbstractContainerScreen` 带类型参数（`AbstractContainerScreen[C <: AbstractContainerMenu]`），
 * 而 1.7.10 那种「trait 直接 extends 基类」的写法会让界面必须写成
 * `with LockedHotbar[container.X]`（Scala 的 Java 泛型是不变的，`AbstractContainerScreen[container.X]`
 * 并不是 `AbstractContainerScreen[AbstractContainerMenu]` 的子类型）。
 *
 * 因此改成「基类声明扩展点 + trait 只实现扩展点」：
 *  - [[li.cil.oc.client.gui.CustomGuiContainer]] 混入本 trait，把两个扩展点接到
 *    `slotClicked` / `checkHotbarKeyPressed` 上，并提供「不锁定」的默认实现；
 *  - [[LockedHotbar]] 覆写这两个扩展点。
 *
 * 这样界面侧仍然是 `extends DynamicGuiContainer[container.X] with LockedHotbar`，
 * 与 1.7.10 的写法完全一致。
 */
trait SlotLocking {
  /** 该槽位是否禁止被点击 / 拖放（true 表示这次点击要丢弃）。 */
  protected def isSlotLocked(slot: Slot): Boolean

  /** 是否禁止快捷键（1-9 / F / 换手键）把悬停槽位与快捷栏交换。 */
  protected def isHotbarKeyLocked: Boolean
}

/**
 * 锁定某个槽位，使它不能被点击，也不参与快捷栏交换
 * （原 1.7.10 的 `li.cil.oc.client.gui.traits.LockedHotbar`）。
 *
 * 用途：平板 / 服务器 / 数据库界面里，代表「本体物品」的那个槽位不允许被玩家拿走。
 */
trait LockedHotbar extends SlotLocking {
  /** 需要被锁定的那叠物品（通常就是打开界面的那件物品）。 */
  def lockedStack: ItemStack

  /** 原实现：`slot != null && slot.getStack == lockedStack` 时丢弃点击。 */
  override protected def isSlotLocked(slot: Slot): Boolean =
    slot != null && slot.getItem != null && slot.getItem == lockedStack

  /** 原实现：`checkHotbarKeys(keyCode) = false`，即完全不处理快捷栏按键。 */
  override protected def isHotbarKeyLocked: Boolean = true
}
