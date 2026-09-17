package li.cil.oc.common.tileentity.traits

import li.cil.oc.common.inventory
import li.cil.oc.common.tileentity.ItemHandlerProvider
import li.cil.oc.util.InventoryUtils
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * 带物品栏的方块实体（对应 1.7.10 的 `common.tileentity.traits.Inventory`）。
 *
 * 1.21.1 迁移要点：
 *  - 物品栏后端改为 `common.inventory.Inventory`（基于 `IItemHandler`），
 *    原来的 `IInventory#getSizeInventory` 变成 `IItemHandler#getSlots`。
 *  - **具体方块实体必须覆写 `getSlots`**（原 `getSizeInventory`）：下面的后端数组就是按它
 *    分配的，不覆写会退化成 `Inventory#getSlots = items.length` 的自我引用。
 *  - `InventoryUtils` 已改为面向 `IItemHandler` + `BlockPosition`。
 *  - `ForgeDirection` → `Direction`（1.21.1 没有 `UNKNOWN`），因此
 *    `dropSlot` / `spawnStackInWorld` 的 `direction` 参数是 `Option[Direction]`，`None` 表示
 *    「从方块中心朝随机方向抛出」，与原 `None` 语义一致。
 *  - 混入 [[li.cil.oc.common.tileentity.ItemHandlerProvider]]，让 `Registry` 把本方块实体
 *    注册成 NeoForge 的 `Capabilities.ItemHandler.BLOCK` 提供者（漏斗 / 管道可访问）。
 *    因为 `inventory.Inventory` 本身就是 `IItemHandler`，这里不需要额外代码。
 */
trait Inventory extends TileEntity with inventory.Inventory with ItemHandlerProvider {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  private lazy val inventory = Array.fill[Option[ItemStack]](getSlots)(None)

  override def items: Array[Option[ItemStack]] = inventory

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    save(nbt)
  }

  // ----------------------------------------------------------------------- //

  /**
   * 玩家是否还能操作这个物品栏（原 `IInventory#isUseableByPlayer`）。
   * `IInventory` 已移除，这里保留为普通方法供容器使用。
   */
  def isUseableByPlayer(player: Player): Boolean =
    player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) <= 64

  // ----------------------------------------------------------------------- //

  /**
   * 把某个槽位的内容掉落到世界中（原 `dropSlot(slot, count, direction)`）。
   *
   * 1.7.10 的 `count` 默认值是全区统一的 `getInventoryStackLimit`；Scala 不允许默认参数引用
   * 同一参数列表里在它之前的 `slot`，因此这里用 `-1` 表示「取该槽位的容量」，行为与旧默认值一致。
   */
  def dropSlot(slot: Int, count: Int = -1, direction: Option[Direction] = None): Boolean =
    InventoryUtils.dropSlot(position, this, slot, if (count < 0) getSlotLimit(slot) else count, direction)

  def dropAllSlots(): Unit =
    InventoryUtils.dropAllSlots(position, this)

  def spawnStackInWorld(stack: ItemStack, direction: Option[Direction] = None) =
    InventoryUtils.spawnStackInWorld(position, stack, direction)
}
