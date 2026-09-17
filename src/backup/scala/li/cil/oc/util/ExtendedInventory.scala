package li.cil.oc.util

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

import scala.collection.mutable
import scala.language.implicitConversions

/**
 * `IItemHandler` 的只读序列视图。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `net.neoforged.neoforge.items.IItemHandler`
 *  - `getSizeInventory` → `getSlots`，`getStackInSlot` 同名
 *  - `setInventorySlotContents` → `insertItem`（新 API 不允许直接覆盖槽位），
 *    因此 `update` 现在只能“插入”，无法用空栈清空槽位。
 */
object ExtendedInventory {

  implicit def extendedInventory(inventory: IItemHandler): ExtendedInventory = new ExtendedInventory(inventory)

  class ExtendedInventory(val inventory: IItemHandler) extends mutable.IndexedSeq[ItemStack] {
    override def length: Int = inventory.getSlots

    override def update(idx: Int, elem: ItemStack): Unit =
      if (elem != null && !elem.isEmpty) inventory.insertItem(idx, elem, false)

    override def apply(idx: Int): ItemStack = inventory.getStackInSlot(idx)
  }

}
