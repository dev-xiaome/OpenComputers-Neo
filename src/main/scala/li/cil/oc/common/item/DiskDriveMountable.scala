package li.cil.oc.common.item

import java.util

import li.cil.oc.client.KeyBindings
import li.cil.oc.{OpenComputers, Settings}
import li.cil.oc.common.GuiType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

import scala.collection.mutable

class DiskDriveMountable(val parent: Delegator) extends traits.Delegate {
  override def maxStackSize = 1

  override def onItemRightClick(stack: ItemStack, world: Level, player: Player) = {
    // Open the GUI immediately on the client, too, to avoid the player
    // changing the current slot before it actually opens, which can lead to
    // desynchronization of the player inventory.
    player.openGui(OpenComputers, GuiType.DiskDriveMountable.id, world, 0, 0, 0)
    player.swingItem()
    stack
  }
}
