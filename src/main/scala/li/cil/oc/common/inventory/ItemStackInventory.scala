package li.cil.oc.common.inventory

import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

trait ItemStackInventory extends Inventory {
  // The item stack that provides the inventory.
  def container: ItemStack

  private lazy val inventory = Array.fill[Option[ItemStack]](getSizeInventory)(None)

  override def items = inventory

  // Initialize the list automatically if we have a container.
  if (container != null) {
    reinitialize()
  }

  // Load items from tag.
  def reinitialize(): Unit = {
    for (i <- items.indices) {
      updateItems(i, null)
    }
    if (!container.hasTagCompound) {
      container.put(new CompoundTag())
    }
    load(container.getTagCompound)
  }

  // Write items back to tag.
  override def markDirty(): Unit = {
    if (!container.hasTagCompound) {
      container.put(new CompoundTag())
    }
    save(container.getTagCompound)
  }
}
