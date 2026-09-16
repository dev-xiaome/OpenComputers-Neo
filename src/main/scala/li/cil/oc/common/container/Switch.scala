package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Inventory

/**
 * 交换机容器（原 1.7.10 `container.Switch`）。
 *
 * 1.21.1 迁移同 [[Relay]]：`Container` → `AbstractContainerMenu`（构造器多 `windowId`
 * + [[MenuTypes]] 的 `MenuType`）；`getInteger` → `getInt`。
 */
// TODO Remove in 1.7
class Switch(windowId: Int, playerInventory: Inventory, val switch: tileentity.Switch)
  extends Player(windowId, MenuTypes.Switch.value(), playerInventory, switch) {

  addSlotToContainer(151, 15, Slot.CPU)
  addSlotToContainer(151, 34, Slot.Memory)
  addSlotToContainer(151, 53, Slot.HDD)
  addPlayerInventorySlots(8, 84)

  /** 宿主的显示名（原 1.7.10 的 `switch.getInventoryName`）；见 [[DiskDrive.driveName]] 的说明。 */
  def switchName: String = switch.getInventoryName

  def relayDelay: Int = synchronizedData.getInt("relayDelay")

  def relayAmount: Int = synchronizedData.getInt("relayAmount")

  def maxQueueSize: Int = synchronizedData.getInt("maxQueueSize")

  def packetsPerCycleAvg: Int = synchronizedData.getInt("packetsPerCycleAvg")

  def queueSize: Int = synchronizedData.getInt("queueSize")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putInt("relayDelay", switch.relayDelay)
    synchronizedData.putInt("relayAmount", switch.relayAmount)
    synchronizedData.putInt("maxQueueSize", switch.maxQueueSize)
    synchronizedData.putInt("packetsPerCycleAvg", switch.packetsPerCycleAvg())
    synchronizedData.putInt("queueSize", switch.queue.size)
    super.detectCustomDataChanges(nbt)
  }
}
