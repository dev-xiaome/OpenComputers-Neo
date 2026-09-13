package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory

class Adapter(playerInventory: Inventory, adapter: tileentity.Adapter) extends Player(playerInventory, adapter) {
  addSlotToContainer(80, 35, Slot.Upgrade)
  addPlayerInventorySlots(8, 84)
}
