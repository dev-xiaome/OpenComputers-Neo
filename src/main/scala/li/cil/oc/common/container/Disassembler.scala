package li.cil.oc.common.container

import li.cil.oc.common.tileentity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Inventory

/**
 * 拆卸机容器（原 1.7.10 `container.Disassembler`）。
 *
 * 1.21.1 迁移：`Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 * `NBTTagCompound#getInteger` → `getInt`。
 */
class Disassembler(windowId: Int, playerInventory: Inventory, val disassembler: tileentity.Disassembler)
  extends Player(windowId, MenuTypes.Disassembler.value(), playerInventory, disassembler) {

  addSlotToContainer(80, 35, "ocitem")
  addPlayerInventorySlots(8, 84)

  def disassemblyProgress: Double = synchronizedData.getDouble("disassemblyProgress")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putDouble("disassemblyProgress", disassembler.progress)
    super.detectCustomDataChanges(nbt)
  }
}
