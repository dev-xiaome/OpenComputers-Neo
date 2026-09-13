package li.cil.oc.common.container

import li.cil.oc.common.item.TabletWrapper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory

class Tablet(playerInventory: Inventory, tablet: TabletWrapper) extends Player(playerInventory, tablet) {
  addSlotToContainer(new StaticComponentSlot(this, otherInventory, otherInventory.getSizeInventory - 1, 80, 35, tablet.containerSlotType, tablet.containerSlotTier))

  addPlayerInventorySlots(8, 84)

  override def canInteractWith(player: Player) = player == playerInventory.player
}
