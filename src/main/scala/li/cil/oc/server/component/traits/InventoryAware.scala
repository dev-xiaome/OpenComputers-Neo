package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.util.ExtendedArguments._
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.IItemHandlerModifiable

/**
 * 带内部物品栏的组件（对应 1.7.10 的 `traits.InventoryAware`）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `IItemHandler`；`getSizeInventory` → `getSlots`
 *  - `IItemHandler` 没有“直接覆盖槽位”的方法，清空/交换槽位需要先 `extractItem`
 *    再 `insertItem`，这里用 [[InventorySlots]] 兜住。
 */
trait InventoryAware {
  def fakePlayer: Player

  def inventory: IItemHandler

  def selectedSlot: Int

  def selectedSlot_=(value: Int): Unit

  def insertionSlots: Iterable[Int] = (selectedSlot until inventory.getSlots) ++ (0 until selectedSlot)

  // ----------------------------------------------------------------------- //

  protected def optSlot(args: Arguments, n: Int) =
    if (args.count > 0 && args.checkAny(0) != null) args.checkSlot(inventory, 0)
    else selectedSlot

  /** 指定槽位的物品；空槽返回 `None`（旧的 `null` 语义）。 */
  protected def stackInSlot(slot: Int): Option[ItemStack] =
    Option(inventory.getStackInSlot(slot)).filterNot(_.isEmpty)

  /** 底层（可能为 `null` / 空栈）的槽位内容，等价于 1.7.10 的 `inventory.getStackInSlot`。 */
  protected def rawStackInSlot(slot: Int): ItemStack = inventory.getStackInSlot(slot)

  /** 用指定物品覆盖槽位；`null` 或空栈表示清空。 */
  protected def setStackInSlot(slot: Int, stack: ItemStack): Unit =
    InventorySlots.setStack(if (inventory == null) null else InventorySlots.wrap(inventory), slot, stack)

  /** 标记物品栏已变更（1.7.10 的 `IInventory#markDirty`）。 */
  protected def markInventoryDirty(): Unit =
    if (inventory != null) InventorySlots.markDirty(inventory)
}

/**
 * `IItemHandler` 的槽位写入辅助。
 *
 * 1.21.1 的 `IItemHandler` 只有“插入 / 抽取”语义，没有 1.7.10 `IInventory` 的
 * `setInventorySlotContents` / `decrStackSize`。若物品栏同时实现了
 * [[IItemHandlerModifiable]] 则直接覆盖槽位；否则退化为“先抽空、再插入”。
 */
object InventorySlots {

  /** 槽位的最大容量（用于一次性抽空）。 */
  private def slotSize(inventory: IItemHandler, slot: Int): Int = {
    if (inventory == null || slot < 0 || slot >= inventory.getSlots) return 0
    val stack = inventory.getStackInSlot(slot)
    if (stack == null || stack.isEmpty) 0 else stack.getCount
  }

  def wrap(inventory: IItemHandler): IItemHandlerModifiable = inventory match {
    case modifiable: IItemHandlerModifiable => modifiable
    case _ => new ReadOnlyBackedHandler(inventory)
  }

  def setStack(inventory: IItemHandlerModifiable, slot: Int, stack: ItemStack): Unit = {
    if (inventory == null || slot < 0 || slot >= inventory.getSlots) return
    if (stack == null || stack.isEmpty) {
      clear(inventory, slot)
    }
    else {
      clear(inventory, slot)
      inventory.setStackInSlot(slot, stack)
    }
  }

  /** 抽空某个槽位。 */
  def clear(inventory: IItemHandler, slot: Int): Unit = {
    if (inventory == null || slot < 0 || slot >= inventory.getSlots) return
    inventory match {
      case modifiable: IItemHandlerModifiable => modifiable.setStackInSlot(slot, ItemStack.EMPTY)
      case _ =>
        val count = slotSize(inventory, slot)
        if (count > 0) inventory.extractItem(slot, count, false)
    }
    ()
  }

  /** 从槽位中取出至多 `count` 个物品（1.7.10 的 `decrStackSize`）。 */
  def decrStackSize(inventory: IItemHandler, slot: Int, count: Int): ItemStack = {
    if (inventory == null || slot < 0 || slot >= inventory.getSlots || count <= 0) return ItemStack.EMPTY
    val extracted = inventory.extractItem(slot, count, false)
    if (extracted == null) ItemStack.EMPTY else extracted
  }

  /**
   * TODO(server): 1.21.1 的 `IItemHandler` 没有 `markDirty`/`setChanged`（那是
   * `Container` 与 `BlockEntity` 的职责），这里保留为无操作，由宿主方块实体自行 `setChanged`。
   */
  def markDirty(inventory: IItemHandler): Unit = ()

  /**
   * 把 [[IItemHandler]] 伪装成 [[IItemHandlerModifiable]]：读操作全部转发，
   * 写操作退化为“抽空 + 插入”。仅在底层物品栏确实可写时才有意义。
   */
  private class ReadOnlyBackedHandler(inner: IItemHandler) extends IItemHandlerModifiable {
    override def setStackInSlot(slot: Int, stack: ItemStack): Unit = {
      clear(inner, slot)
      if (stack != null && !stack.isEmpty) inner.insertItem(slot, stack, false)
    }

    override def getSlots: Int = inner.getSlots

    override def getStackInSlot(slot: Int): ItemStack = inner.getStackInSlot(slot)

    override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack =
      inner.insertItem(slot, stack, simulate)

    override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack =
      inner.extractItem(slot, amount, simulate)

    override def getSlotLimit(slot: Int): Int = inner.getSlotLimit(slot)

    override def isItemValid(slot: Int, stack: ItemStack): Boolean = inner.isItemValid(slot, stack)
  }
}
