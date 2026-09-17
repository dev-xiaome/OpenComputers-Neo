package li.cil.oc.common.inventory

import li.cil.oc.api.Driver
import li.cil.oc.api.internal
import li.cil.oc.common.InventorySlots
import li.cil.oc.util.ItemUtils
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * 服务器（机架上的服务器组件）内部物品栏
 * （对应 1.7.10 的 `common.inventory.ServerInventory`）。
 *
 * 1.21.1 迁移要点：`getSizeInventory` → `getSlots`，`getInventoryStackLimit` → `getSlotLimit`，
 * `isItemValidForSlot` → `isItemValid`；`isUseableByPlayer` 不再是接口方法，保留为普通方法。
 */
trait ServerInventory extends ItemStackInventory {
  def tier: Int = ItemUtils.caseTier(container) max 0

  override def getSlots: Int = InventorySlots.server(tier).length

  override protected def inventoryName: String = "Server"

  override def getSlotLimit(slot: Int): Int = 1

  def isUseableByPlayer(player: Player): Boolean = false

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    Option(Driver.driverFor(stack, classOf[internal.Server])).fold(false)(driver => {
      val provided = InventorySlots.server(tier)(slot)
      driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
    })
}
