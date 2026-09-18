package li.cil.oc.common.container

import li.cil.oc.Settings
import li.cil.oc.api.{ImmutableItemStack, Persistable}
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.util.StackOption
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.{DataComponentHolder, DataComponentType}
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.nbt.Tag
import net.neoforged.neoforge.common.MutableDataComponentHolder

trait Inventory extends SimpleInventory with Persistable {
  def items: Array[ItemStack]

  def updateItems(slot: Int, stack: ItemStack): Unit = items(slot) = StackOption(stack).orEmpty

  // ----------------------------------------------------------------------- //

  override def getItem(slot: Int): ItemStack =
    if (slot >= 0 && slot < getContainerSize && slot < items.length) items(slot)
    else ItemStack.EMPTY

  override def setItem(slot: Int, stack: ItemStack): Unit = {
    if (slot >= 0 && slot < getContainerSize && slot < items.length) {
      if (stack.isEmpty && items(slot).isEmpty) {
        return
      }
      if (items(slot) == stack) {
        return
      }

      val oldStack = items(slot)
      updateItems(slot, ItemStack.EMPTY)
      if (!oldStack.isEmpty) {
        onItemRemoved(slot, oldStack)
      }
      if (!stack.isEmpty && stack.getCount >= getInventoryStackRequired) {
        if (stack.getCount > getMaxStackSize) {
          stack.setCount(getMaxStackSize)
        }
        updateItems(slot, stack)
      }

      if (!items(slot).isEmpty) {
        onItemAdded(slot, items(slot))
      }

      setChanged()
    }
  }

  override def getName: Component = Component.translatable(Settings.namespace + "container." + inventoryName)

  protected def inventoryName: String = getClass.getSimpleName.toLowerCase

  override def isEmpty: Boolean = items.forall(_.isEmpty)

  // ----------------------------------------------------------------------- //

  def loadFrom(value: Iterable[ImmutableItemStack]): Unit = {
    // Restore serialized inventory entries one-to-one by slot.
    //
    // The previous port accidentally used a nested for-comprehension here,
    // producing a Cartesian product: every saved stack was written to every
    // slot in turn. That destroys slot-sensitive inventories such as robots
    // (floppy/tool/main inventory and dynamic component positions).
    val saved = value.iterator
    var slot = 0

    while (slot < items.length) {
      items(slot) =
        if (saved.hasNext) saved.next().mutableCopy()
        else ItemStack.EMPTY
      slot += 1
    }
  }

  def component: DataComponentType[List[ImmutableItemStack]] =
    OCComponents.CONTENTS.get()

  override def loadData(holder: DataComponentHolder): Unit = {
    for(items <- holder.getComponent(this.component)) {
      loadFrom(items)
    }
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(this.component, items.map(ImmutableItemStack.copyOf).toList)
  }

  // ----------------------------------------------------------------------- //

  protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {}

  protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {}
}
