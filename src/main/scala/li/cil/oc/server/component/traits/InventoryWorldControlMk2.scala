package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 与相邻物品栏按“指定槽位”交互（对应 1.7.10 的 `traits.InventoryWorldControlMk2`）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `IItemHandler`：`getSizeInventory` → `getSlots`
 *  - `stack.stackSize` → `stack.getCount` / `stack.isEmpty`
 *  - `setInventorySlotContents` / `markDirty` → [[InventorySlots]] 与 [[InventoryAware]]
 */
trait InventoryWorldControlMk2 extends InventoryAware with WorldAware with SideRestricted {
  @Callback(doc = """function(facing:number, slot:number[, count:number[, fromSide:number]]):boolean -- Drops the selected item stack into the specified slot of an inventory.""")
  def dropIntoSlot(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val count = args.optItemCount(2)
    val fromSide = args.optSideAny(3, facing.getOpposite)
    val stack = inventory.getStackInSlot(selectedSlot)
    if (stack != null && !stack.isEmpty) {
      withInventory(position.offset(facing), fromSide, target => {
        val slot = args.checkSlot(target, 1)
        if (!InventoryUtils.insertIntoInventorySlot(stack, target, Option(fromSide), slot, count)) {
          // Cannot drop into that inventory.
          return result(false, "inventory full/invalid slot")
        }
        else if (stack.isEmpty) {
          // Dropped whole stack.
          setStackInSlot(selectedSlot, ItemStack.EMPTY)
        }
        else {
          // Dropped partial stack. 1.21.1 的物品栏不保证返回可写的槽位引用，显式写回。
          setStackInSlot(selectedSlot, stack)
          markInventoryDirty()
        }

        context.pause(Settings.get.dropDelay)

        result(true)
      })
    }
    else result(false)
  }

  @Callback(doc = """function(facing:number, slot:number[, count:number[, fromSide:number]]):boolean -- Sucks items from the specified slot of an inventory.""")
  def suckFromSlot(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val count = args.optItemCount(2)
    val fromSide = args.optSideAny(3, facing.getOpposite)
    withInventory(position.offset(facing), fromSide, target => {
      val slot = args.checkSlot(target, 1)
      val extracted = InventoryUtils.extractFromInventorySlot(InventoryUtils.insertIntoInventory(_, this.inventory, slots = Option(insertionSlots)), target, fromSide, slot, count)
      if (extracted > 0) {
        context.pause(Settings.get.suckDelay)
        result(extracted)
      }
      else result(false)
    })
  }

  private def withInventory(blockPos: BlockPosition, fromSide: Direction, f: IItemHandler => Array[AnyRef]) =
    InventoryUtils.inventoryAt(blockPos) match {
      // TODO(server): 1.21.1 的 IItemHandler 没有 `isUseableByPlayer` 的等价查询，
      // 旧版对“该物品栏是否可被此玩家使用”的检查退化为只检查交互权限。
      case Some(target) if mayInteract(blockPos, fromSide) => f(target)
      case _ => result(Unit, "no inventory")
    }
}
