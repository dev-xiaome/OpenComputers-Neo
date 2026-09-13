package li.cil.oc.common.inventory

import li.cil.oc.api.Driver
import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.world.item.ItemStack

/**
 * 软驱 / 磁盘驱动器里可挂载物品对应的物品栏
 * （对应 1.7.10 的 `common.inventory.DiskDriveMountableInventory`）。
 *
 * 1.21.1 迁移要点：`getSizeInventory` → `getSlots`，`getInventoryStackLimit` → `getSlotLimit`，
 * `isItemValidForSlot` → `isItemValid`。驱动器类型仍是 `common.tileentity.DiskDrive`。
 */
trait DiskDriveMountableInventory extends ItemStackInventory {
  def tier: Int = 1

  override def getSlots: Int = 1

  override protected def inventoryName: String = "DiskDrive"

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, classOf[tileentity.DiskDrive]))) match {
    case (0, Some(driver)) => driver.slot(stack) == Slot.Floppy
    case _ => false
  }
}
