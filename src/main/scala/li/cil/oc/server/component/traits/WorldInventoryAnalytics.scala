package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.ItemStackArrayValue
import li.cil.oc.server.component.result
import li.cil.oc.util.{BlockInventorySource, BlockPosition, DatabaseAccess, EntityInventorySource, InventorySource, InventoryUtils}
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util.ExtendedArguments._
import net.minecraft.core.Direction
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.items.IItemHandler

/**
 * 世界中方块/实体物品栏的信息检索（对应 1.7.10 的 `traits.WorldInventoryAnalytics`）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `IItemHandler`：`getSizeInventory` → `getSlots`，`stackSize` → `getCount`，
 *    空槽用 `isEmpty` 判断（不再有 `null`）
 *  - `OreDictionary` 已移除，`areStacksEquivalent` 改用物品/方块 tag（[[InventoryAnalytics.haveSharedTag]]）
 *  - `Block#getUnlocalizedName` → `Block#getName`（`Component#getString`）
 *  - `EntityList.getEntityString` → `EntityType.getKey(entity.getType)`
 */
trait WorldInventoryAnalytics extends WorldAware with SideRestricted with NetworkAware {
  @Callback(doc = """function(side:number):number -- Get the number of slots in the inventory on the specified side of the device.""")
  def getInventorySize(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => result(inventory.getSlots))
  }

  @Callback(doc = """function(side:number, slot:number):number -- Get number of items in the specified slot of the inventory on the specified side of the device.""")
  def getSlotStackSize(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => result(Option(inventory.getStackInSlot(args.checkSlot(inventory, 1))).fold(0)(_.getCount)))
  }

  @Callback(doc = """function(side:number, slot:number):number -- Get the maximum number of items in the specified slot of the inventory on the specified side of the device.""")
  def getSlotMaxStackSize(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => result(Option(inventory.getStackInSlot(args.checkSlot(inventory, 1))).fold(0)(_.getMaxStackSize)))
  }

  @Callback(doc = """function(side:number, slotA:number, slotB:number[, checkNBT:boolean=false]):boolean -- Get whether the items in the two specified slots of the inventory on the specified side of the device are of the same type.""")
  def compareStacks(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => {
      val stackA = inventory.getStackInSlot(args.checkSlot(inventory, 1))
      val stackB = inventory.getStackInSlot(args.checkSlot(inventory, 2))
      result(stackA == stackB || InventoryUtils.haveSameItemType(stackA, stackB, args.optBoolean(3, false)))
    })
  }

  @Callback(doc = """function(side:number, slot:number, dbAddress:string, dbSlot:number[, checkNBT:boolean=false]):boolean -- Compare an item in the specified slot in the inventory on the specified side with one in the database with the specified address.""")
  def compareStackToDatabase(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => {
      val slot = args.checkSlot(inventory, 1)
      val dbAddress = args.checkString(2)
      val stack = inventory.getStackInSlot(slot)
      DatabaseAccess.withDatabase(node, dbAddress, database => {
        val dbSlot = args.checkSlot(database.data, 3)
        val dbStack = database.getStackInSlot(dbSlot)
        result(InventoryUtils.haveSameItemType(stack, dbStack, args.optBoolean(4, false)))
      })
    })
  }

  @Callback(doc = """function(side:number, slotA:number, slotB:number):boolean -- Get whether the items in the two specified slots of the inventory on the specified side of the device are equivalent (have shared OreDictionary IDs).""")
  def areStacksEquivalent(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => {
      val stackA = inventory.getStackInSlot(args.checkSlot(inventory, 1))
      val stackB = inventory.getStackInSlot(args.checkSlot(inventory, 2))
      // 1.21.1 的矿辞已被物品 tag 取代，判定逻辑见 InventoryAnalytics.haveSharedTag。
      result(stackA == stackB || InventoryAnalytics.haveSharedTag(stackA, stackB))
    })
  }

  @Callback(doc = """function(side:number, slot:number):table -- Get a description of the stack in the inventory on the specified side of the device.""")
  def getStackInSlot(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => result(inventory.getStackInSlot(args.checkSlot(inventory, 1))))
  }
  else result(Unit, "not enabled in config")

  @Callback(doc = """function(side:number):userdata -- Get a description of all stacks in the inventory on the specified side of the device.""")
  def getAllStacks(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => {
        val stacks = new Array[ItemStack](inventory.getSlots)
        for(i <- 0 until inventory.getSlots){
          stacks(i) = inventory.getStackInSlot(i)
        }
        result(new ItemStackArrayValue(stacks))
      })
  }
  else result(Unit, "not enabled in config")

  @Callback(doc = """function(side:number):string -- Get the the name of the inventory on the specified side of the device.""")
  def getInventoryName(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val facing = checkSideForAction(args, 0)
    def blockAt(position: BlockPosition): Option[Block] = position.world match {
      case Some(world) if world.blockExists(position) => world.getBlock(position) match {
        case block: Block => Some(block)
        case _ => None
      }
      case _ => None
    }
    withInventorySource(facing, {
      case BlockInventorySource(position, _) => blockAt(position) match {
        // 1.21.1 的方块名是 Component（`Block#getName`），旧版是 `getUnlocalizedName`。
        case Some(block) => result(block.getName.getString)
        case _ => result(Unit, "Unknown")
      }
      case EntityInventorySource(entity, _) => result(EntityType.getKey(entity.getType).toString)
      case _ => result(Unit, "Unknown")
    })
  }
  else result(Unit, "not enabled in config")

  @Callback(doc = """function(side:number, slot:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack description in the specified slot of the database with the specified address.""")
  def store(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val dbAddress = args.checkString(2)
    def store(stack: ItemStack) = DatabaseAccess.withDatabase(node, dbAddress, database => {
      val dbSlot = args.checkSlot(database.data, 3)
      val nonEmpty = database.getStackInSlot(dbSlot) != null
      database.setStackInSlot(dbSlot, stack.copy())
      result(nonEmpty)
    })
    withInventory(facing, inventory => store(inventory.getStackInSlot(args.checkSlot(inventory, 1))))
  }

  private def mayInteract(side: Direction, f: InventorySource): Boolean = {
    // TODO(server): 1.21.1 的 IItemHandler 没有 `isUseableByPlayer` 的等价查询
    //（旧版用它判断玩家能否使用该物品栏，例如原版工作台/铁砧），这里退化为只检查方块交互权限。
    f match {
      case BlockInventorySource(pos, _) => mayInteract(pos, side.getOpposite)
      case _ => true
    }
  }

  private def withInventorySource(side: Direction, f: InventorySource => Array[AnyRef]) =
    InventoryUtils.inventorySourceAt(position.offset(side)) match {
      case Some(is) if mayInteract(side, is) => f(is)
      case _ => result(Unit, "no inventory")
    }

  private def withInventory(side: Direction, f: IItemHandler => Array[AnyRef]) =
    withInventorySource(side, is => f(is.inventory))
}
