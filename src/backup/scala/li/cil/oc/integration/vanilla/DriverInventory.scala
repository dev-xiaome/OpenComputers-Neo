package li.cil.oc.integration.vanilla

import li.cil.oc.Settings
import li.cil.oc.api.driver.{NamedBlock, SidedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.{BlockPosition, ResultWrapper}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.items.IItemHandler

/**
 * 通用物品栏驱动（原 `DriverInventory.java`）。
 *
 * 1.21.1 迁移要点（改动较大，因此由 Java 改写为 Scala）：
 *  - 1.7.10 的 `net.minecraft.inventory.IInventory` 接口已不存在，
 *    方块物品栏统一通过 NeoForge 能力 `Capabilities.ItemHandler.BLOCK` 取得 `IItemHandler`；
 *    因此 `getTileEntityClass() = IInventory.class` 这种「按接口匹配」的写法不再可行，
 *    改为直接实现 [[SidedBlock]]，在 `worksWith` 里查询能力是否存在。
 *  - `getSizeInventory` → `getSlots`；`getInventoryStackLimit` → `getSlotLimit`
 *  - `setInventorySlotContents` / `decrStackSize` → `insertItem` / `extractItem`
 *    （新 API 是「插入返回剩余物、抽取返回实际抽出物」的语义）
 *  - `markDirty` → 由 `IItemHandler` 实现自行处理（能力层调用后即代表已写入）
 *  - `isItemEqual` → `ItemStack.isSameItemSameComponents`
 *  - `stack.stackSize` → `stack.getCount`，空堆叠判断用 `isEmpty`
 *
 * TODO(port): 1.7.10 的 `notPermitted()` 通过 `ForgeEventFactory.onPlayerInteract`
 * 触发 `PlayerInteractEvent` 再叠加 `IInventory#isUseableByPlayer` 判定访问权限；
 * 1.21.1 触发右键事件需要 `CommonHooks.onRightClickBlock`，它会连带执行真实交互逻辑，
 * 对「只是扫描一下邻居」的场景并不合适。这里降级为只调用 `Container#stillValid`。
 */
object DriverInventory extends SidedBlock {
  override def worksWith(world: Level, x: Int, y: Int, z: Int, side: Direction): Boolean =
    itemHandlerAt(world, new BlockPos(x, y, z), side) != null

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment = {
    val pos = new BlockPos(x, y, z)
    val handler = itemHandlerAt(world, pos, side)
    if (handler == null) null
    else new Environment(handler, world, world.getBlockEntity(pos), BlockPosition(x, y, z, world))
  }

  /**
   * 通过 NeoForge 方块能力查询物品栏。
   *
   * `side` 为 `null`（`Registry.driverFor` 的「不限定面」查询）时由能力提供方自行决定；
   * 无物品栏时返回 `null`。
   */
  private def itemHandlerAt(world: Level, pos: BlockPos, side: Direction): IItemHandler = {
    if (world == null || !world.isLoaded(pos)) null
    else Capabilities.ItemHandler.BLOCK.getCapability(world, pos, world.getBlockState(pos), world.getBlockEntity(pos), side)
  }

  final class Environment(handler: IItemHandler, world: Level, blockEntity: BlockEntity, blockPosition: BlockPosition)
    extends ManagedTileEntityEnvironment[IItemHandler](handler, "inventory") with NamedBlock {

    override def preferredName = "inventory"

    override def priority = 0

    @Callback(doc = "function():string -- Get the name of this inventory.")
    def getInventoryName(context: Context, args: Arguments): Array[AnyRef] = {
      if (notPermitted()) return ResultWrapper.result(null, "permission denied")
      // TODO(port): `IItemHandler` 没有名字；1.7.10 返回的是 `IInventory#getInventoryName`。
      // 这里退化为方块自身的显示名（1.21.1 的 `Block` 没有 `getName`，改用其翻译键）。
      val block = world.getBlockState(blockPosition.toChunkCoordinates).getBlock
      ResultWrapper.result(Component.translatable(block.getDescriptionId).getString)
    }

    @Callback(doc = "function():number -- Get the number of slots in this inventory.")
    def getInventorySize(context: Context, args: Arguments): Array[AnyRef] = {
      if (notPermitted()) return ResultWrapper.result(null, "permission denied")
      ResultWrapper.result(tileEntity.getSlots)
    }

    @Callback(doc = "function(slot:number):number -- Get the stack size of the item stack in the specified slot.")
    def getSlotStackSize(context: Context, args: Arguments): Array[AnyRef] = {
      if (notPermitted()) return ResultWrapper.result(null, "permission denied")
      val slot = checkSlot(args, 0)
      val stack = tileEntity.getStackInSlot(slot)
      if (stack != null && !stack.isEmpty) ResultWrapper.result(stack.getCount)
      else ResultWrapper.result(0)
    }

    @Callback(doc = "function(slot:number):number -- Get the maximum stack size of the item stack in the specified slot.")
    def getSlotMaxStackSize(context: Context, args: Arguments): Array[AnyRef] = {
      if (notPermitted()) return ResultWrapper.result(null, "permission denied")
      val slot = checkSlot(args, 0)
      val stack = tileEntity.getStackInSlot(slot)
      val limit = tileEntity.getSlotLimit(slot)
      if (stack != null && !stack.isEmpty) ResultWrapper.result(math.min(limit, stack.getMaxStackSize))
      else ResultWrapper.result(limit)
    }

    @Callback(doc = "function(slotA:number, slotB:number):boolean -- Compare the two item stacks in the specified slots for equality.")
    def compareStacks(context: Context, args: Arguments): Array[AnyRef] = {
      if (notPermitted()) return ResultWrapper.result(null, "permission denied")
      val slotA = checkSlot(args, 0)
      val slotB = checkSlot(args, 1)
      if (slotA == slotB) return ResultWrapper.result(true)
      val stackA = tileEntity.getStackInSlot(slotA)
      val stackB = tileEntity.getStackInSlot(slotB)
      val emptyA = stackA == null || stackA.isEmpty
      val emptyB = stackB == null || stackB.isEmpty
      if (emptyA && emptyB) ResultWrapper.result(true)
      else if (!emptyA && !emptyB) ResultWrapper.result(ItemStack.isSameItemSameComponents(stackA, stackB))
      else ResultWrapper.result(false)
    }

    @Callback(doc = "function(slotA:number, slotB:number[, count:number=math.huge]):boolean -- Move up to the specified number of items from the first specified slot to the second.")
    def transferStack(context: Context, args: Arguments): Array[AnyRef] = {
      if (notPermitted()) return ResultWrapper.result(null, "permission denied")
      val slotA = checkSlot(args, 0)
      val slotB = checkSlot(args, 1)
      val count = math.max(0, math.min(
        if (args.count > 2 && args.checkAny(2) != null) args.checkInteger(2) else 64,
        tileEntity.getSlotLimit(slotA)))
      if (slotA == slotB || count == 0) {
        return ResultWrapper.result(true)
      }
      val stackA = tileEntity.getStackInSlot(slotA)
      val stackB = tileEntity.getStackInSlot(slotB)
      if (stackA == null || stackA.isEmpty) {
        // 源槽位为空。
        ResultWrapper.result(false)
      }
      else if (stackB == null || stackB.isEmpty) {
        // 目标为空槽，直接搬运。
        move(slotA, slotB, math.min(count, stackA.getCount))
      }
      else if (ItemStack.isSameItemSameComponents(stackA, stackB)) {
        // 同种物品，堆叠。
        val space = math.min(tileEntity.getSlotLimit(slotB), stackB.getMaxStackSize) - stackB.getCount
        val amount = math.min(count, math.min(space, stackA.getCount))
        if (amount > 0) move(slotA, slotB, amount)
        else ResultWrapper.result(false)
      }
      else if (count >= stackA.getCount) {
        // 交换两个槽位。
        val extractedA = tileEntity.extractItem(slotA, stackA.getCount, false)
        val extractedB = tileEntity.extractItem(slotB, stackB.getCount, false)
        val remainderA = if (extractedA == null) null else tileEntity.insertItem(slotB, extractedA, false)
        val remainderB = if (extractedB == null) null else tileEntity.insertItem(slotA, extractedB, false)
        putBack(slotA, remainderA)
        putBack(slotB, remainderB)
        ResultWrapper.result(true)
      }
      else {
        ResultWrapper.result(false)
      }
    }

    @Callback(doc = "function(slot:number):table -- Get a description of the item stack in the specified slot.")
    def getStackInSlot(context: Context, args: Arguments): Array[AnyRef] = {
      if (Settings.get.allowItemStackInspection) {
        if (notPermitted()) return ResultWrapper.result(null, "permission denied")
        val stack = tileEntity.getStackInSlot(checkSlot(args, 0))
        if (stack == null || stack.isEmpty) ResultWrapper.result(null) else ResultWrapper.result(stack)
      }
      else ResultWrapper.result(null, "not enabled in config")
    }

    @Callback(doc = "function():table -- Get a list of descriptions for all item stacks in this inventory.")
    def getAllStacks(context: Context, args: Arguments): Array[AnyRef] = {
      if (Settings.get.allowItemStackInspection) {
        if (notPermitted()) return ResultWrapper.result(null, "permission denied")
        val allStacks = new Array[AnyRef](tileEntity.getSlots)
        for (i <- 0 until tileEntity.getSlots) {
          val stack = tileEntity.getStackInSlot(i)
          allStacks(i) = if (stack == null || stack.isEmpty) null else stack
        }
        ResultWrapper.result(allStacks: AnyRef)
      }
      else ResultWrapper.result(null, "not enabled in config")
    }

    /** 从 `from` 搬运至多 `amount` 个物品到 `to`，返回本次调用是否成功移动了物品。 */
    private def move(from: Int, to: Int, amount: Int): Array[AnyRef] = {
      val extracted = tileEntity.extractItem(from, amount, true)
      if (extracted == null || extracted.isEmpty) ResultWrapper.result(false)
      else {
        val remainder = tileEntity.insertItem(to, extracted, false)
        val moved = extracted.getCount - (if (remainder == null) 0 else remainder.getCount)
        if (moved <= 0) ResultWrapper.result(false)
        else {
          tileEntity.extractItem(from, moved, false)
          ResultWrapper.result(true)
        }
      }
    }

    /** 把插入失败的剩余物放回原槽位。 */
    private def putBack(slot: Int, stack: ItemStack): Unit =
      if (stack != null && !stack.isEmpty) tileEntity.insertItem(slot, stack, false)

    private def checkSlot(args: Arguments, number: Int): Int = {
      val slot = args.checkInteger(number) - 1
      if (slot < 0 || slot >= tileEntity.getSlots) {
        throw new IllegalArgumentException("slot index out of bounds")
      }
      slot
    }

    private def notPermitted(): Boolean = {
      if (blockEntity == null) false
      else blockEntity match {
        case container: Container => world match {
          case serverLevel: ServerLevel =>
            try {
              val player = FakePlayerFactory.get(serverLevel, Settings.get.fakePlayerProfile)
              player.setPos(blockPosition.x + 0.5, blockPosition.y + 0.5, blockPosition.z + 0.5)
              !container.stillValid(player)
            }
            catch {
              case _: Throwable => false
            }
          case _ => false
        }
        case _ => false
      }
    }
  }

}
