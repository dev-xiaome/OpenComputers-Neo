package li.cil.oc.common.tileentity.traits

import li.cil.oc.common.inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

// Used to get notifications from containers when a player changes a slot in
// this inventory. Normally the player causing a setInventorySlotContents is
// unavailable. Using this we gain access to the causing player, allowing for
// some player-specific logic, such as the disassembler working instantaneously
// when used by a player in creative mode.
//
// 1.21.1 迁移：`IInventory` → `common.inventory.Inventory`（基于 `IItemHandler`），
// 方法签名不变，`container.ComponentSlot` 依旧通过类型匹配回调它。
trait PlayerInputAware extends inventory.Inventory {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  def onSetInventorySlotContents(player: Player, slot: Int, stack: ItemStack): Unit
}
