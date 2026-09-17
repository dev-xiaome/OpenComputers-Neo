package li.cil.oc.client.gui

import li.cil.oc.common.menu
import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class Tablet(state: menu.Tablet, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name)
  with traits.LockedHotbar[menu.Tablet] {

  override def lockedStack = inventoryContainer.stack
}
