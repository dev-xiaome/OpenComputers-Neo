package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Inventory

/**
 * 打印机容器（原 1.7.10 `container.Printer`）。
 *
 * 1.21.1 迁移：`Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 * `NBTTagCompound#getInteger` → `getInt`。
 */
class Printer(windowId: Int, playerInventory: Inventory, val printer: tileentity.Printer)
  extends Player(windowId, MenuTypes.Printer.value(), playerInventory, printer) {

  addSlotToContainer(18, 19, Slot.Filtered)
  addSlotToContainer(18, 51, Slot.Filtered)
  addSlotToContainer(152, 35)

  // Show the player's inventory.
  addPlayerInventorySlots(8, 84)

  def progress: Double = synchronizedData.getDouble("progress")

  def amountMaterial: Int = synchronizedData.getInt("amountMaterial")

  def amountInk: Int = synchronizedData.getInt("amountInk")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putDouble("progress", if (printer.isPrinting) printer.progress / 100.0 else 0)
    synchronizedData.putInt("amountMaterial", printer.amountMaterial)
    synchronizedData.putInt("amountInk", printer.amountInk)
    super.detectCustomDataChanges(nbt)
  }
}
