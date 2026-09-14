package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Inventory

/**
 * 中继器容器（原 1.7.10 `container.Relay`）。
 *
 * 1.21.1 迁移要点：`Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 * `NBTTagCompound#getInteger` → `getInt`；`Hub#packetsPerCycleAvg` 由 `MovingAverage` 提供，
 * 仍然用 `apply()` 取值。
 */
class Relay(windowId: Int, playerInventory: Inventory, relay: tileentity.Relay)
  extends Player(windowId, MenuTypes.Relay.value(), playerInventory, relay) {

  addSlotToContainer(151, 15, Slot.CPU)
  addSlotToContainer(151, 34, Slot.Memory)
  addSlotToContainer(151, 53, Slot.HDD)
  addSlotToContainer(178, 15, Slot.Card)
  addPlayerInventorySlots(8, 84)

  def relayDelay: Int = synchronizedData.getInt("relayDelay")

  def relayAmount: Int = synchronizedData.getInt("relayAmount")

  def maxQueueSize: Int = synchronizedData.getInt("maxQueueSize")

  def packetsPerCycleAvg: Int = synchronizedData.getInt("packetsPerCycleAvg")

  def queueSize: Int = synchronizedData.getInt("queueSize")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putInt("relayDelay", relay.relayDelay)
    synchronizedData.putInt("relayAmount", relay.relayAmount)
    synchronizedData.putInt("maxQueueSize", relay.maxQueueSize)
    synchronizedData.putInt("packetsPerCycleAvg", relay.packetsPerCycleAvg())
    synchronizedData.putInt("queueSize", relay.queue.size)
    super.detectCustomDataChanges(nbt)
  }
}
