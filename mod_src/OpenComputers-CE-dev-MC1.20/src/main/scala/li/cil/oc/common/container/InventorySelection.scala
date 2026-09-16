package li.cil.oc.common.container

trait InventorySelection {
  def selectedSlot: Int

  def selectedSlot_=(value: Int): Unit
}
