package li.cil.oc.common.container

import li.cil.oc.common.inventory.DatabaseInventory
import net.minecraft.world.entity.player.{Inventory, Player => MCPlayer}

/**
 * 数据库（物品形态）容器（原 1.7.10 `container.Database`）。
 *
 * 1.21.1 迁移要点：
 *  - `Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 *  - `getSizeInventory` → [[net.neoforged.neoforge.items.IItemHandler#getSlots]]；
 *  - `canInteractWith` → `stillValid`。
 */
class Database(windowId: Int, playerInventory: Inventory, databaseInventory: DatabaseInventory)
  extends Player(windowId, MenuTypes.Database.value(), playerInventory, databaseInventory) {

  val rows = math.sqrt(databaseInventory.getSlots).ceil.toInt
  val offset = 8 + Array(3, 2, 0)(databaseInventory.tier) * slotSize

  for (row <- 0 until rows; col <- 0 until rows) {
    addSlotToContainer(offset + col * slotSize, offset + row * slotSize)
  }

  // Show the player's inventory.
  addPlayerInventorySlots(8, 174)

  override def stillValid(player: MCPlayer): Boolean = player == playerInventory.player
}
