package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.item.ItemStack

import scala.jdk.CollectionConverters._

/**
 * 组件内部物品栏的物品信息检索（对应 1.7.10 的 `traits.InventoryAnalytics`）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `IItemHandler`：`getSizeInventory` → `getSlots`
 *  - `OreDictionary` 已移除，等价判断改为“是否共享物品/方块 tag”（见 [[haveSharedTag]]）。
 */
trait InventoryAnalytics extends InventoryAware with NetworkAware {
  @Callback(doc = """function([slot:number]):table -- Get a description of the stack in the specified slot or the selected slot.""")
  def getStackInInternalSlot(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val slot = optSlot(args, 0)
    result(rawStackInSlot(slot))
  }
  else result(Unit, "not enabled in config")

  @Callback(doc = """function(otherSlot:number):boolean -- Get whether the stack in the selected slot is equivalent to the item in the specified slot (have shared OreDictionary IDs).""")
  def isEquivalentTo(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = args.checkSlot(inventory, 0)
    result((stackInSlot(selectedSlot), stackInSlot(slot)) match {
      case (Some(stackA), Some(stackB)) => haveSharedTag(stackA, stackB)
      case (None, None) => true
      case _ => false
    })
  }

  @Callback(doc = """function(slot:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack description in the specified slot of the database with the specified address.""")
  def storeInternal(context: Context, args: Arguments): Array[AnyRef] = {
    val localSlot = args.checkSlot(inventory, 0)
    val dbAddress = args.checkString(1)
    val localStack = rawStackInSlot(localSlot)
    DatabaseAccess.withDatabase(node, dbAddress, database => {
      val dbSlot = args.checkSlot(database.data, 2)
      val nonEmpty = database.data.getStackInSlot(dbSlot) != null
      database.data match {
        case modifiable: net.neoforged.neoforge.items.IItemHandlerModifiable =>
          modifiable.setStackInSlot(dbSlot, if (localStack == null) ItemStack.EMPTY else localStack.copy())
        case _ =>
          InventorySlots.setStack(InventorySlots.wrap(database.data), dbSlot,
            if (localStack == null) ItemStack.EMPTY else localStack.copy())
      }
      result(nonEmpty)
    })
  }

  @Callback(doc = """function(slot:number, dbAddress:string, dbSlot:number[, checkNBT:boolean=false]):boolean -- Compare an item in the specified slot with one in the database with the specified address.""")
  def compareToDatabase(context: Context, args: Arguments): Array[AnyRef] = {
    val localSlot = args.checkSlot(inventory, 0)
    val dbAddress = args.checkString(1)
    val localStack = rawStackInSlot(localSlot)
    DatabaseAccess.withDatabase(node, dbAddress, database => {
      val dbSlot = args.checkSlot(database.data, 2)
      val dbStack = database.data.getStackInSlot(dbSlot)
      result(InventoryUtils.haveSameItemType(localStack, dbStack, args.optBoolean(3, false)))
    })
  }

  /**
   * 两个物品堆是否“等价”。
   *
   * 1.7.10 用 `OreDictionary.getOreIDs` 求交集；1.21.1 的矿辞已被物品 tag 取代，
   * 因此改为判断两者是否共享任意物品 tag 或方块 tag。
   */
  private def haveSharedTag(stackA: ItemStack, stackB: ItemStack): Boolean = {
    if (stackA == null || stackB == null || stackA.isEmpty || stackB.isEmpty) return false
    if (stackA.is(stackB.getItem)) return true
    val tagsA = stackA.getTags.map(_.location.toString).toSet
    val tagsB = stackB.getTags.map(_.location.toString).toSet
    if (tagsA.intersect(tagsB).nonEmpty) return true
    val blockA = net.minecraft.world.level.block.Block.byItem(stackA.getItem)
    val blockB = net.minecraft.world.level.block.Block.byItem(stackB.getItem)
    if (blockA == null || blockB == null || blockA == net.minecraft.world.level.block.Blocks.AIR) return false
    val blockTagsA = blockA.builtInRegistryHolder().tags().map(_.location().toString).toSet
    val blockTagsB = blockB.builtInRegistryHolder().tags().map(_.location().toString).toSet
    blockTagsA.intersect(blockTagsB).nonEmpty
  }
}
