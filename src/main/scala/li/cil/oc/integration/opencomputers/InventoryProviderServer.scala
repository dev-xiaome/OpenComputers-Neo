package li.cil.oc.integration.opencomputers

import li.cil.oc.api.driver.InventoryProvider
import li.cil.oc.common.inventory.ServerInventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

object InventoryProviderServer extends InventoryProvider {
  override def worksWith(stack: ItemStack, player: Player): Boolean = DriverServer.worksWith(stack)

  // 1.21.1：`net.minecraft.inventory.IInventory` 已由 NeoForge 的 `IItemHandler` 取代。
  override def getInventory(stack: ItemStack, player: Player): IItemHandler = new ServerInventory {
    override def container: ItemStack = stack
  }
}
