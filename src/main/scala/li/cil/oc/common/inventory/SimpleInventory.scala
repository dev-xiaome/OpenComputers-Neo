package li.cil.oc.common.inventory

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 物品栏的「规模信息」抽象层（对应 1.7.10 的 `li.cil.oc.common.inventory.SimpleInventory`）。
 *
 * 1.7.10 的 `IInventory` 在 1.21.1 已被 NeoForge 的 `IItemHandler` 取代，映射关系：
 * {{{
 *  getSizeInventory          → getSlots
 *  getInventoryStackLimit    → getSlotLimit(slot)（旧名字保留为兼容转发）
 *  getInventoryStackRequired → getInventoryStackRequired（幽灵槽位语义，保持原名）
 *  isItemValidForSlot        → isItemValid(slot, stack)
 *  decrStackSize             → extractItem(slot, amount, simulate)
 *  markDirty()               → 由持有者实现（方块实体走 `traits.TileEntity#markDirty`）
 * }}}
 */
trait SimpleInventory extends IItemHandler {
  /** 槽位数量（原 `getSizeInventory`）。 */
  override def getSlots: Int

  /**
   * 指定槽位的容量（原 `getInventoryStackLimit`；1.7.10 里是全区统一值）。
   *
   * 默认转发到 [[getInventoryStackLimit]]，这样 1.7.10 里覆写旧名字的子类也能生效；
   * 新代码请直接覆写本方法。
   */
  override def getSlotLimit(slot: Int): Int = getInventoryStackLimit

  /** 兼容 1.7.10 的统一容量查询；新代码请覆写 [[getSlotLimit]]。 */
  def getInventoryStackLimit: Int = 64

  /** 槽位里至少要放这么多物品才算「有物品」（幽灵槽位用，保持原名）。 */
  def getInventoryStackRequired: Int = 1

  /** 是否允许把 `stack` 放进 `slot`（原 `isItemValidForSlot`）。 */
  override def isItemValid(slot: Int, stack: ItemStack): Boolean = true

  /**
   * 标记需要存盘（原 `IInventory#markDirty`）。
   *
   * `IItemHandler` 已没有这个概念，因此声明为抽象：方块实体由
   * [[li.cil.oc.common.tileentity.traits.TileEntity#markDirty]] 提供实现，
   * 物品 / 实体持有的物品栏由各自覆写。
   */
  def markDirty(): Unit

  /**
   * 原 `decrStackSize`：从槽位取出物品，转发到 `IItemHandler#extractItem`。
   *
   * 1.7.10 的约定是「什么都没抽到返回 `null`」，而 `extractItem` 返回 `ItemStack.EMPTY`；
   * 调用方（`DiskDrive#eject`、`Printer#update`、`Disassembler#tick` 等）普遍写的是
   * `if (stack != null)`，因此这里必须把空堆叠统一还原成 `null`，否则语义会反过来。
   */
  def decrStackSize(slot: Int, amount: Int): ItemStack = {
    val extracted = extractItem(slot, amount, false)
    if (extracted == null || extracted.isEmpty) null else extracted
  }
}
