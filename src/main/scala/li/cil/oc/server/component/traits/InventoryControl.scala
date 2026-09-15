package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.world.item.ItemStack

/**
 * 组件的内部物品栏控制（对应 1.7.10 的 `traits.InventoryControl`）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory#getSizeInventory` → `IItemHandler#getSlots`
 *  - `stack.stackSize` → `stack.getCount` / `stack.setCount`
 *  - `inventory.setInventorySlotContents` / `decrStackSize` → [[InventorySlots]] 的
 *    `setStack` / `decrStackSize`（`IItemHandler` 只有插入/抽取语义）
 *  - `inventory.getInventoryStackLimit` → `IItemHandler#getSlotLimit(slot)`
 */
trait InventoryControl extends InventoryAware {
  @Callback(doc = "function():number -- The size of this device's internal inventory.")
  def inventorySize(context: Context, args: Arguments): Array[AnyRef] = result(inventory.getSlots)

  @Callback(doc = "function([slot:number]):number -- Get the currently selected slot; set the selected slot if specified.")
  def select(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = optSlot(args, 0)
    if (slot != selectedSlot) {
      selectedSlot = slot
    }
    result(selectedSlot + 1)
  }

  @Callback(direct = true, doc = "function([slot:number]):number -- Get the number of items in the specified slot, otherwise in the selected slot.")
  def count(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = optSlot(args, 0)
    result(stackInSlot(slot) match {
      case Some(stack) => stack.getCount
      case _ => 0
    })
  }

  @Callback(direct = true, doc = "function([slot:number]):number -- Get the remaining space in the specified slot, otherwise in the selected slot.")
  def space(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = optSlot(args, 0)
    result(stackInSlot(slot) match {
      case Some(stack) => math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize) - stack.getCount
      case _ => inventory.getSlotLimit(slot)
    })
  }

  @Callback(doc = "function(otherSlot:number[, checkNBT:boolean=false]):boolean -- Compare the contents of the selected slot to the contents of the specified slot.")
  def compareTo(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = args.checkSlot(inventory, 0)
    result((stackInSlot(selectedSlot), stackInSlot(slot)) match {
      case (Some(stackA), Some(stackB)) => InventoryUtils.haveSameItemType(stackA, stackB, args.optBoolean(1, false))
      case (None, None) => true
      case _ => false
    })
  }

  @Callback(doc = "function(toSlot:number[, amount:number]):boolean -- Move up to the specified amount of items from the selected slot into the specified slot.")
  def transferTo(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = args.checkSlot(inventory, 0)
    val count = args.optItemCount(1)
    if (slot == selectedSlot || count == 0) {
      result(true)
    }
    else result((stackInSlot(selectedSlot), stackInSlot(slot)) match {
      case (Some(from), Some(to)) =>
        if (InventoryUtils.haveSameItemType(from, to, checkNBT = true)) {
          val space = math.min(inventory.getSlotLimit(slot), to.getMaxStackSize) - to.getCount
          val amount = math.min(count, math.min(space, from.getCount))
          if (amount > 0) {
            val moved = InventorySlots.decrStackSize(inventory, selectedSlot, amount)
            if (moved != null && !moved.isEmpty) {
              InventoryUtils.insertIntoInventorySlot(moved, inventory, None, slot)
            }
            true
          }
          else false
        }
        else if (count >= from.getCount) {
          // 整栈交换：先把两个槽位都取出来，再分别放回去。
          val a = InventorySlots.decrStackSize(inventory, selectedSlot, from.getCount)
          val b = InventorySlots.decrStackSize(inventory, slot, to.getCount)
          InventorySlots.setStack(InventorySlots.wrap(inventory), slot, a)
          InventorySlots.setStack(InventorySlots.wrap(inventory), selectedSlot, b)
          true
        }
        else false
      case (Some(from), None) =>
        val moved = InventorySlots.decrStackSize(inventory, selectedSlot, count)
        if (moved == null || moved.isEmpty) false
        else {
          InventorySlots.setStack(InventorySlots.wrap(inventory), slot, moved)
          true
        }
      case _ => false
    })
  }
}
