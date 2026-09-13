package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.nbt.CompoundTag

// TODO Remove in 1.7
class Switch(playerInventory: Inventory, switch: tileentity.Switch) extends Player(playerInventory, switch) {
  addSlotToContainer(151, 15, Slot.CPU)
  addSlotToContainer(151, 34, Slot.Memory)
  addSlotToContainer(151, 53, Slot.HDD)
  addPlayerInventorySlots(8, 84)

  def relayDelay = synchronizedData.getInteger("relayDelay")

  def relayAmount = synchronizedData.getInteger("relayAmount")

  def maxQueueSize = synchronizedData.getInteger("maxQueueSize")

  def packetsPerCycleAvg = synchronizedData.getInteger("packetsPerCycleAvg")

  def queueSize = synchronizedData.getInteger("queueSize")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putInt("relayDelay", switch.relayDelay)
    synchronizedData.putInt("relayAmount", switch.relayAmount)
    synchronizedData.putInt("maxQueueSize", switch.maxQueueSize)
    synchronizedData.putInt("packetsPerCycleAvg", switch.packetsPerCycleAvg())
    synchronizedData.putInt("queueSize", switch.queue.size)
    super.detectCustomDataChanges(nbt)
  }
}
