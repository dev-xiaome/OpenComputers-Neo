package li.cil.oc.common.inventory

/**
 * 「当前选中的槽位」状态（对应 1.7.10 的 `common.inventory.InventorySelection`）。
 *
 * 1.21.1 迁移：无变化，纯粹的状态抽象，同样由机器人等实体物品栏实现。
 */
trait InventorySelection {
  def selectedSlot: Int

  def selectedSlot_=(value: Int): Unit
}
