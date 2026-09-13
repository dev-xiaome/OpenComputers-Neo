package li.cil.oc.common.inventory

import li.cil.oc.api.Driver
import li.cil.oc.api.internal
import li.cil.oc.common.InventorySlots
import li.cil.oc.util.ItemUtils
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

trait ServerInventory extends ItemStackInventory {
  def tier: Int = ItemUtils.caseTier(container) max 0

  override def getSizeInventory = InventorySlots.server(tier).length

  override protected def inventoryName = "Server"

  override def getInventoryStackLimit = 1

  override def isUseableByPlayer(player: Player) = false

  override def isItemValidForSlot(slot: Int, stack: ItemStack) =
    Option(Driver.driverFor(stack, classOf[internal.Server])).fold(false)(driver => {
      val provided = InventorySlots.server(tier)(slot)
      driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
    })
}
