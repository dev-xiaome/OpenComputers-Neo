package li.cil.oc.server.component.traits

import li.cil.oc.api
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 物品自带的物品栏（背包等）与设备内部物品栏之间的转移。
 * 对应 1.7.10 的 `traits.ItemInventoryControl`。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `IItemHandler`：`getSizeInventory` → `getSlots`
 *  - 旧版的 `ForgeDirection.UNKNOWN`（“面无关”）在 1.21.1 没有对应物：
 *    `IItemHandler` 的工具方法已忽略 `side` 参数，这里相应传 `None` / `null`。
 */
trait ItemInventoryControl extends InventoryAware {
  @Callback(doc = "function(slot:number):number -- The size of an item inventory in the specified slot.")
  def getItemInventorySize(context: Context, args: Arguments): Array[AnyRef] = {
    withItemInventory(args.checkSlot(inventory, 0), itemInventory => result(itemInventory.getSlots))
  }

  @Callback(doc = "function(inventorySlot:number, slot:number[, count:number=64]):number -- Drops an item into the specified slot in the item inventory.")
  def dropIntoItemInventory(context: Context, args: Arguments): Array[AnyRef] = {
    withItemInventory(args.checkSlot(inventory, 0), itemInventory => {
      val slot = args.checkSlot(itemInventory, 1)
      val count = args.optItemCount(2)
      // `side` 在新 API 里不再使用（物品能力没有“面”的概念），传 None / null 表达旧版的 UNKNOWN。
      result(InventoryUtils.extractAnyFromInventory(InventoryUtils.insertIntoInventorySlot(_, itemInventory, None, slot), inventory, null, count))
    })
  }

  @Callback(doc = "function(inventorySlot:number, slot:number[, count:number=64]):number -- Sucks an item out of the specified slot in the item inventory.")
  def suckFromItemInventory(context: Context, args: Arguments): Array[AnyRef] = {
    withItemInventory(args.checkSlot(inventory, 0), itemInventory => {
      val slot = args.checkSlot(itemInventory, 1)
      val count = args.optItemCount(2)
      result(InventoryUtils.extractFromInventorySlot(InventoryUtils.insertIntoInventory(_, inventory, slots = Option(insertionSlots)), itemInventory, null, slot, count))
    })
  }

  private def withItemInventory(slot: Int, f: IItemHandler => Array[AnyRef]): Array[AnyRef] = {
    val stack = inventory.getStackInSlot(slot)
    if (stack == null || stack.isEmpty) result(0, "no item inventory")
    else api.Driver.inventoryFor(stack, fakePlayer) match {
      case itemInventory: IItemHandler => f(itemInventory)
      case _ => result(0, "no item inventory")
    }
  }
}
