package li.cil.oc.common.inventory

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 把某个物品栏的一段槽位「投影」成独立物品栏
 * （对应 1.7.10 的 `common.inventory.InventoryProxy`）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `IItemHandler`：`getSizeInventory` → `getSlots`，`setInventorySlotContents`
 *    → `insertItem`，`decrStackSize` → `extractItem`，`isItemValidForSlot` → `isItemValid`。
 *  - 原 `offset` / `isValidSlot` 的映射规则原样保留：对外槽位 `slot` 映射到
 *    底层槽位 `slot + offset`，合法范围由 [[getSlots]] 与 [[offset]] 共同决定。
 *  - `getStackInSlot` 在 1.21.1 必须返回 `ItemStack.EMPTY` 而不是 `null`。
 */
trait InventoryProxy extends IItemHandler {
  def inventory: IItemHandler

  def offset: Int = 0

  /** 暴露的槽位数量（原 `getSizeInventory`），默认与底层物品栏等长。 */
  override def getSlots: Int = inventory.getSlots

  override def getSlotLimit(slot: Int): Int = {
    val offsetSlot = slot + offset
    if (isValidSlot(offsetSlot)) inventory.getSlotLimit(offsetSlot)
    else 0
  }

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = {
    val offsetSlot = slot + offset
    isValidSlot(offsetSlot) && inventory.isItemValid(offsetSlot, stack)
  }

  override def getStackInSlot(slot: Int): ItemStack = {
    val offsetSlot = slot + offset
    if (isValidSlot(offsetSlot)) inventory.getStackInSlot(offsetSlot)
    else ItemStack.EMPTY
  }

  override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = {
    val offsetSlot = slot + offset
    if (isValidSlot(offsetSlot)) inventory.insertItem(offsetSlot, stack, simulate)
    else stack
  }

  override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = {
    val offsetSlot = slot + offset
    if (isValidSlot(offsetSlot)) inventory.extractItem(offsetSlot, amount, simulate)
    else ItemStack.EMPTY
  }

  /**
   * 原 `IInventory#markDirty`。1.21.1 的 `IItemHandler` 没有该概念，
   * 这里在底层物品栏恰好是 OC 方块实体时转发存盘标记。
   */
  def markDirty(): Unit = inventory match {
    case tileEntity: li.cil.oc.common.tileentity.traits.TileEntity => tileEntity.markDirty()
    case _ =>
  }

  private def isValidSlot(slot: Int): Boolean = slot >= offset && slot < getSlots + offset
}
