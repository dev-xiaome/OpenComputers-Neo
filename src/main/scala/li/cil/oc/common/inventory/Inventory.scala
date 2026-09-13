package li.cil.oc.common.inventory

import li.cil.oc.Settings
import li.cil.oc.util.ExtendedNBT
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.item.ItemStack

/**
 * 以 `Array[Option[ItemStack]]` 为后端的物品栏（对应 1.7.10 的 `common.inventory.Inventory`）。
 *
 * 1.21.1 迁移要点：
 *  - 实现 `net.neoforged.neoforge.items.IItemHandler`（`getSlots` / `getStackInSlot` /
 *    `insertItem` / `extractItem` / `getSlotLimit` / `isItemValid`）。
 *  - 原 `setInventorySlotContents` 在 `IItemHandler` 里没有对应方法，但「先移除、再添加」的
 *    通知顺序是组件热插拔的关键，因此保留为普通方法（内部由 `insertItem` / `extractItem` 复用）。
 *  - `ItemStack.loadItemStackFromNBT` → `ItemStack.parseOptional`，
 *    `stack.writeToNBT` → `stack.save`（都需要注册表访问器，见 `ExtendedNBT.fallbackRegistry`）。
 *
 * 注意：`updateItems(slot, null)` 保留「传 `null` 表示清空槽位」的语义——数据库的幽灵槽位
 * （数量为 0 的 `ItemStack`）需要被原样保存，不能用 `ItemStack.EMPTY` 代替。
 */
trait Inventory extends SimpleInventory {
  def items: Array[Option[ItemStack]]

  /** 写入某个槽位；传 `null` 表示清空（与原实现一致）。 */
  def updateItems(slot: Int, stack: ItemStack): Unit = items(slot) = Option(stack)

  // ----------------------------------------------------------------------- //
  // IItemHandler 实现
  // ----------------------------------------------------------------------- //

  override def getSlots: Int = items.length

  override def getStackInSlot(slot: Int): ItemStack =
    if (isValidSlot(slot)) items(slot).getOrElse(ItemStack.EMPTY)
    else ItemStack.EMPTY

  /**
   * 向槽位插入物品：合并到已有堆叠，或放进空槽位；返回**没能插入**的部分。
   *
   * `simulate` 为真时只计算不修改状态。实际写入走 [[setInventorySlotContents]]，
   * 以复用原有的 add / remove 通知逻辑。
   */
  override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = {
    if (stack == null || stack.isEmpty || !isValidSlot(slot)) return stack
    if (!isItemValid(slot, stack)) return stack

    val (current, template) = items(slot) match {
      case Some(inSlot) if inSlot != null && !inSlot.isEmpty =>
        // 只有同种物品（含组件）才能合并，否则整栈拒绝。
        if (!inSlot.is(stack.getItem) || !ItemStack.isSameItemSameComponents(inSlot, stack)) return stack
        (inSlot.getCount, inSlot)
      case _ => (0, stack)
    }

    val inserted = math.min(math.max(0, getSlotLimit(slot) - current), stack.getCount)
    if (inserted <= 0) return stack

    if (!simulate) {
      setInventorySlotContents(slot, template.copyWithCount(current + inserted))
    }
    stack.copyWithCount(stack.getCount - inserted)
  }

  /**
   * 从槽位抽取物品；返回实际抽出的堆叠（空堆叠表示什么都没抽到）。
   *
   * 语义对齐原 `decrStackSize`：抽到剩下的数量低于 [[SimpleInventory.getInventoryStackRequired]]
   * 时整槽清空（并触发移除通知），否则就地削减数量、不触发 add / remove 通知。
   */
  override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = {
    if (amount <= 0 || !isValidSlot(slot)) return ItemStack.EMPTY
    items(slot) match {
      case Some(stack) if stack != null && !stack.isEmpty =>
        if (stack.getCount - amount < getInventoryStackRequired) {
          // 整槽取出：走 setInventorySlotContents 以触发 onItemRemoved。
          if (simulate) stack.copy()
          else {
            setInventorySlotContents(slot, null)
            stack
          }
        }
        else {
          val extracted = math.min(amount, stack.getCount)
          if (simulate) stack.copyWithCount(extracted)
          else {
            // 原实现用 `splitStack` 就地修改，不触发 add / remove 通知。
            val result = stack.copyWithCount(extracted)
            stack.shrink(extracted)
            markDirty()
            result
          }
        }
      case _ => ItemStack.EMPTY
    }
  }

  // ----------------------------------------------------------------------- //
  // 旧 API（保留语义，供物品栏 / GUI / 组件层使用）
  // ----------------------------------------------------------------------- //

  /**
   * 原 `IInventory#setInventorySlotContents`。
   *
   * 保留「先 updateItems(null) → onItemRemoved → onItemAdded」的顺序：组件层依赖它来
   * 先卸载旧组件、再安装新组件。
   */
  def setInventorySlotContents(slot: Int, stack: ItemStack): Unit = {
    if (isValidSlot(slot)) {
      if (stack == null && items(slot).isEmpty) {
        return
      }
      if (stack != null && items(slot).contains(stack)) {
        return
      }

      val oldStack = items(slot)
      updateItems(slot, null)
      if (oldStack.isDefined) {
        onItemRemoved(slot, oldStack.get)
      }
      if (stack != null && stack.getCount >= getInventoryStackRequired) {
        val limit = getSlotLimit(slot)
        if (stack.getCount > limit) {
          stack.setCount(limit)
        }
        updateItems(slot, stack)
      }

      if (items(slot).isDefined) {
        onItemAdded(slot, items(slot).get)
      }

      markDirty()
    }
  }

  /** 原 `getInventoryName`；`IItemHandler` 没有名字概念，保留供 GUI 显示使用。 */
  def getInventoryName: String = Settings.namespace + "container." + inventoryName

  protected def inventoryName: String = getClass.getSimpleName

  // ----------------------------------------------------------------------- //
  // 存盘
  // ----------------------------------------------------------------------- //

  def load(nbt: CompoundTag): Unit = {
    // Implicit slot numbers are compatibility code for loading old server save format.
    // TODO 1.7 remove compat code.
    var count = 0
    nbt.getList(Settings.namespace + "items", Tag.TAG_COMPOUND).foreach((tag: CompoundTag) => {
      if (tag.contains("slot")) {
        val slot = tag.getByte("slot")
        if (slot >= 0 && slot < items.length) {
          updateItems(slot, loadStack(tag.getCompound("item")))
        }
      }
      else {
        val slot = count
        if (slot >= 0 && slot < items.length) {
          updateItems(slot, loadStack(tag))
        }
      }
      count += 1
    })
  }

  def save(nbt: CompoundTag): Unit = {
    nbt.setNewTagList(Settings.namespace + "items",
      items.zipWithIndex.collect {
        case (Some(stack), slot) if stack != null => (stack, slot)
      }.map {
        case (stack, slot) =>
          val slotNbt = new CompoundTag()
          slotNbt.putByte("slot", slot.toByte)
          slotNbt.setNewCompoundTag("item", (tag: CompoundTag) => stack.save(ExtendedNBT.fallbackRegistry, tag))
          slotNbt
      }.toIndexedSeq)
  }

  /** 反序列化单个堆叠；空气 / 解析失败返回 `null`（与旧版 `loadItemStackFromNBT` 一致）。 */
  private def loadStack(tag: CompoundTag): ItemStack = {
    val stack = ItemStack.parseOptional(ExtendedNBT.fallbackRegistry, tag)
    if (stack == null || stack.isEmpty) null else stack
  }

  private def isValidSlot(slot: Int): Boolean = slot >= 0 && slot < items.length

  // ----------------------------------------------------------------------- //
  // 钩子
  // ----------------------------------------------------------------------- //

  protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {}

  protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {}
}
