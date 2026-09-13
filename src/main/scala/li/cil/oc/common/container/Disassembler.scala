package li.cil.oc.common.container

import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.nbt.CompoundTag

class Disassembler(playerInventory: Inventory, val disassembler: tileentity.Disassembler) extends Player(playerInventory, disassembler) {
  addSlotToContainer(80, 35, "ocitem")
  addPlayerInventorySlots(8, 84)

  def disassemblyProgress = synchronizedData.getDouble("disassemblyProgress")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putDouble("disassemblyProgress", disassembler.progress)
    super.detectCustomDataChanges(nbt)
  }
}
