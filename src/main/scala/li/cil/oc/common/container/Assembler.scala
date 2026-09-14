package li.cil.oc.common.container

import com.mojang.datafixers.util.Pair

import li.cil.oc.common
import li.cil.oc.common.InventorySlots.InventorySlot
import li.cil.oc.common.template.AssemblerTemplates
import li.cil.oc.common.tileentity
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

/**
 * 装配机容器（原 1.7.10 `container.Assembler`）。
 *
 * 1.21.1 迁移要点：
 *  - `Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 *  - 槽位背景由 `Icons.get(...)`（`client.gui`，尚未移植）改为 [[SlotIcons]]；
 *  - `func_111238_b()` → `isActive`、`getBackgroundIconIndex` → `getNoItemIcon`、`getStack` → `getItem`；
 *  - `NBTTagCompound#getInteger` → `getInt`。
 */
class Assembler(windowId: Int, playerInventory: Inventory, val assembler: tileentity.Assembler)
  extends Player(windowId, MenuTypes.Assembler.value(), playerInventory, assembler) {

  // Computer case.
  {
    val index = slots.size
    addSlot(new StaticComponentSlot(this, otherInventory, index, 12, 12, "template", common.Tier.Any) {
      override def isActive: Boolean = !isAssembling && super.isActive

      override def getNoItemIcon: Pair[ResourceLocation, ResourceLocation] =
        if (isAssembling) SlotIcons.background(common.Tier.None) else super.getNoItemIcon
    })
  }

  private def slotInfo(slot: DynamicComponentSlot): InventorySlot = {
    // 槽位构成取决于打开的模板。`AssemblerTemplates`（`common/template`）归另一个 agent 负责，
    // 这里沿用它的 1.7.10 语义：containerSlots(3) + upgradeSlots(9) + componentSlots(9)。
    AssemblerTemplates.select(getSlot(0).getItem) match {
      case Some(template) =>
        val index = slot.getSlotIndex
        val tplSlot =
          if ((1 until 4).contains(index)) template.containerSlots(index - 1)
          else if ((4 until 13).contains(index)) template.upgradeSlots(index - 4)
          else if ((13 until 21).contains(index)) template.componentSlots(index - 13)
          else AssemblerTemplates.NoSlot
        new InventorySlot(tplSlot.kind, tplSlot.tier)
      case _ => new InventorySlot(common.Slot.None, common.Tier.None)
    }
  }

  // Component containers.
  for (i <- 0 until 3) {
    addSlotToContainer(34 + i * slotSize, 70, slotInfo _)
  }

  // Components.
  for (i <- 0 until 9) {
    addSlotToContainer(34 + (i % 3) * slotSize, 12 + (i / 3) * slotSize, slotInfo _)
  }

  // Cards.
  for (i <- 0 until 3) {
    addSlotToContainer(104, 12 + i * slotSize, slotInfo _)
  }

  // CPU.
  addSlotToContainer(126, 12, slotInfo _)

  // RAM.
  for (i <- 0 until 2) {
    addSlotToContainer(126, 30 + i * slotSize, slotInfo _)
  }

  // Floppy/EEPROM + HDDs.
  for (i <- 0 until 3) {
    addSlotToContainer(148, 12 + i * slotSize, slotInfo _)
  }

  // Show the player's inventory.
  addPlayerInventorySlots(8, 110)

  def isAssembling: Boolean = synchronizedData.getBoolean("isAssembling")

  def assemblyProgress: Double = synchronizedData.getDouble("assemblyProgress")

  def assemblyRemainingTime: Int = synchronizedData.getInt("assemblyRemainingTime")

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    synchronizedData.putBoolean("isAssembling", assembler.isAssembling)
    synchronizedData.putDouble("assemblyProgress", assembler.progress)
    synchronizedData.putInt("assemblyRemainingTime", assembler.timeRemaining)
    super.detectCustomDataChanges(nbt)
  }
}
