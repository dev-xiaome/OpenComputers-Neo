package li.cil.oc.common.container

import li.cil.oc.Settings
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.menu.{Database => DatabaseContainer}
import li.cil.oc.integration.opencomputers.DriverUpgradeDatabase
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

trait DatabaseInventory extends ItemStackInventory with MenuProvider {
  def tier: Int = DriverUpgradeDatabase.tier(container)

  override def getContainerSize = Settings.get.databaseEntriesPerTier(tier)

  override protected def inventoryName = "database"

  override def getMaxStackSize = 1

  override def getInventoryStackRequired = 1

  override def canPlaceItem(slot: Int, stack: ItemStack) = stack != container

  override def getDisplayName = Component.empty()

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new DatabaseContainer(id, playerInventory, container, this, tier)
}
