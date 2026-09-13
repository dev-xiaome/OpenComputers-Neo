package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.nbt.CompoundTag

class Relay(playerInventory: Inventory, relay: tileentity.Relay) extends Player(playerInventory, relay) {
  addSlotToContainer(151, 15, Slot.CPU)
  addSlotToContainer(151, 34, Slot.Memory)
  addSlotToContainer(151, 53, Slot.HDD)
  addSlotToContainer(178, 15, Slot.Card)
  addPlayerInventorySlots(8, 84)

  def relayDelay = synchronizedData.getInteger("relayDelay")

  def relayAmount = synchronizedData.getInteger("relayAmount")

  def maxQueueSize = synchronizedData.getInteger("maxQueueSize")

  def packetsPerCycleAvg = synchronizedData.getInteger("packetsPerCycleAvg")

  def queueSize = synchronizedData.getInteger("queueSize")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putInt("relayDelay", relay.relayDelay)
    synchronizedData.putInt("relayAmount", relay.relayAmount)
    synchronizedData.putInt("maxQueueSize", relay.maxQueueSize)
    synchronizedData.putInt("packetsPerCycleAvg", relay.packetsPerCycleAvg())
    synchronizedData.putInt("queueSize", relay.queue.size)
    super.detectCustomDataChanges(nbt)
  }
}
