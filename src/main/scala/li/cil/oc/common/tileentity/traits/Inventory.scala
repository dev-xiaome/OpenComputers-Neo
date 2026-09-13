package li.cil.oc.common.tileentity.traits

import li.cil.oc.common.inventory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction

trait Inventory extends BlockEntity with inventory.Inventory {
  private lazy val inventory = Array.fill[Option[ItemStack]](getSizeInventory)(None)

  def items = inventory

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    save(nbt)
  }

  // ----------------------------------------------------------------------- //

  override def isUseableByPlayer(player: Player) =
    player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) <= 64

  // ----------------------------------------------------------------------- //

  def dropSlot(slot: Int, count: Int = getInventoryStackLimit, direction: Option[Direction] = None) =
    InventoryUtils.dropSlot(BlockPosition(x, y, z, world), this, slot, count, direction)

  def dropAllSlots() =
    InventoryUtils.dropAllSlots(BlockPosition(x, y, z, world), this)

  def spawnStackInWorld(stack: ItemStack, direction: Option[Direction] = None) =
    InventoryUtils.spawnStackInWorld(BlockPosition(x, y, z, world), stack, direction)
}
