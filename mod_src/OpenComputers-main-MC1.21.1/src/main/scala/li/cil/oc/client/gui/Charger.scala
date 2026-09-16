package li.cil.oc.client.gui

import li.cil.oc.common.menu
import net.minecraft.world.entity.player.Inventory
import net.minecraft.network.chat.Component

class Charger(state: menu.Charger, playerInventory: Inventory, name: Component)
  extends DynamicGuiContainer(state, playerInventory, name) {
}
