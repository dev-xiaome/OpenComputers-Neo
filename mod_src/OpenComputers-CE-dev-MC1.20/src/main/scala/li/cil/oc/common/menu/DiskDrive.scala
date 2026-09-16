package li.cil.oc.common.menu

import li.cil.oc.common.Slot
import li.cil.oc.common.blockentity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.Container
import net.minecraft.world.inventory.MenuType

class DiskDrive(id: Int, playerInventory: Inventory, drive: Container)
  extends AbstractMenu(MenuTypes.DISK_DRIVE.get(), id, playerInventory, drive) {

  override protected def getHostClass = classOf[blockentity.DiskDrive]

  addSlotToContainer(80, 35, Slot.Floppy)
  addPlayerInventorySlots(8, 84)
}
