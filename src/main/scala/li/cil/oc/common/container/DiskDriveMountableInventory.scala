package li.cil.oc.common.container

import li.cil.oc.api.Driver
import li.cil.oc.common.Slot
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.menu.{DiskDrive => DiskDriveContainer}
import li.cil.oc.common.blockentity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.Component

trait DiskDriveMountableInventory extends ItemStackInventory with MenuProvider {
  def tier: Int = 1

  override def getContainerSize = 1

  override protected def inventoryName = "diskdrive"

  override def getMaxStackSize = 1

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, classOf[blockentity.DiskDrive]))) match {
    case (0, Some(driver)) => driver.slot(stack) == Slot.Floppy
    case _ => false
  }

  override def getDisplayName = Component.empty()

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new DiskDriveContainer(id, playerInventory, this)
}
