package li.cil.oc.common.container

import li.cil.oc.common.Slot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.inventory.IInventory

class DiskDrive(playerInventory: Inventory, drive: IInventory) extends Player(playerInventory, drive) {
  addSlotToContainer(80, 35, Slot.Floppy)
  addPlayerInventorySlots(8, 84)
}
