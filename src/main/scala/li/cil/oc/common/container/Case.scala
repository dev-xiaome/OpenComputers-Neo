package li.cil.oc.common.container

import li.cil.oc.common.{InventorySlots, Tier}
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.{Inventory, Player => MCPlayer}

/**
 * 机箱容器（原 1.7.10 `container.Case`）。
 *
 * 1.21.1 迁移要点：
 *  - `Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 *  - `getInventory.size`（`Container#getInventory`）→ `slots.size`；
 *  - `canInteractWith(player)` → `stillValid(player)`，参数类型改为
 *    [[net.minecraft.world.entity.player.Player]]（本文件里的 `Player` 是 OC 的容器基类，
 *    因此 MC 的玩家类型统一用别名 `MCPlayer`）；
 *  - `player.getCommandSenderName` → `player.getGameProfile.getName`（与
 *    `tileentity.traits.Computer#canInteract(player: String)` 的入参一致）。
 */
class Case(windowId: Int, playerInventory: Inventory, val computer: tileentity.Case)
  extends Player(windowId, MenuTypes.Case.value(), playerInventory, computer) {

  for (i <- 0 to (if (computer.tier >= Tier.Three) 2 else 1)) {
    val slot = InventorySlots.computer(computer.tier)(slots.size)
    addSlotToContainer(98, 16 + i * slotSize, slot.slot, slot.tier)
  }

  for (i <- 0 to (if (computer.tier == Tier.One) 0 else 1)) {
    val slot = InventorySlots.computer(computer.tier)(slots.size)
    addSlotToContainer(120, 16 + (i + 1) * slotSize, slot.slot, slot.tier)
  }

  for (i <- 0 to (if (computer.tier == Tier.One) 0 else 1)) {
    val slot = InventorySlots.computer(computer.tier)(slots.size)
    addSlotToContainer(142, 16 + i * slotSize, slot.slot, slot.tier)
  }

  if (computer.tier >= Tier.Three) {
    val slot = InventorySlots.computer(computer.tier)(slots.size)
    addSlotToContainer(142, 16 + 2 * slotSize, slot.slot, slot.tier)
  }

  {
    val slot = InventorySlots.computer(computer.tier)(slots.size)
    addSlotToContainer(120, 16, slot.slot, slot.tier)
  }

  if (computer.tier == Tier.One) {
    val slot = InventorySlots.computer(computer.tier)(slots.size)
    addSlotToContainer(120, 16 + 2 * slotSize, slot.slot, slot.tier)
  }

  {
    val slot = InventorySlots.computer(computer.tier)(slots.size)
    addSlotToContainer(48, 34, slot.slot, slot.tier)
  }

  // Show the player's inventory.
  addPlayerInventorySlots(8, 84)

  override def stillValid(player: MCPlayer): Boolean =
    super.stillValid(player) && computer.canInteract(player.getGameProfile.getName)
}
