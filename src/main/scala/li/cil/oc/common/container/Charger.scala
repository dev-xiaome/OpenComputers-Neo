package li.cil.oc.common.container

import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory

class Charger(playerInventory: Inventory, charger: tileentity.Charger) extends Player(playerInventory, charger) {
  addSlotToContainer(80, 35, "tablet")
  addPlayerInventorySlots(8, 84)
}
