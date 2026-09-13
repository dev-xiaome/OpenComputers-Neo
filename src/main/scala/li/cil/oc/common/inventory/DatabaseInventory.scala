package li.cil.oc.common.inventory

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import net.minecraft.world.item.ItemStack

/**
 * 数据库升级内部的物品栏（幽灵槽位物品栏）
 * （对应 1.7.10 的 `common.inventory.DatabaseInventory`）。
 *
 * 1.21.1 迁移要点：
 *  - `getSizeInventory` → `getSlots`，`getInventoryStackLimit` → `getSlotLimit`，
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `getSlotLimit = 0` + `getInventoryStackRequired = 0` 是幽灵槽位语义：槽位里保存的是
 *    「数量为 0 的 ItemStack」，只表示物品类型。`inventory.Inventory.updateItems(slot, null)`
 *    的 `null` = 清空语义因此必须保留。
 */
trait DatabaseInventory extends ItemStackInventory {
  /**
   * 数据库升级的等级（原 `DriverUpgradeDatabase.tier(container)`）。
   *
   * TODO(integration.opencomputers.DriverUpgradeDatabase): 物品驱动层尚未纳入编译范围，
   * 这里按同样的语义（1/2/3 级数据库升级物品）直接查 `api.Items`；该驱动移植后改回调用。
   */
  def tier: Int = {
    val descriptor = if (container == null || container.isEmpty) null else api.Items.get(container)
    if (descriptor == null) Tier.One
    else if (isDatabaseUpgrade(Constants.ItemName.DatabaseUpgradeTier3, descriptor)) Tier.Three
    else if (isDatabaseUpgrade(Constants.ItemName.DatabaseUpgradeTier2, descriptor)) Tier.Two
    else Tier.One
  }

  private def isDatabaseUpgrade(name: String, descriptor: api.detail.ItemInfo): Boolean = {
    val candidate = api.Items.get(name)
    candidate != null && candidate == descriptor
  }

  override def getSlots: Int = Settings.get.databaseEntriesPerTier(tier)

  override protected def inventoryName: String = "Database"

  override def getSlotLimit(slot: Int): Int = 0

  override def getInventoryStackRequired: Int = 0

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = stack != container
}
