package li.cil.oc.common.blockentity.traits

import li.cil.oc.api.driver.DriverItem
import li.cil.oc.api.network.{ManagedEnvironment, Node}
import li.cil.oc.common.{EventHandler, container}
import li.cil.oc.util.ExtendedInventory._
import li.cil.oc.util.StackOption
import li.cil.oc.util.StackOption._
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

import scala.collection.mutable

trait ComponentInventory extends Environment with Inventory with container.ComponentInventory {
  override def host = this

  // ----------------------------------------------------------------------- //

  // Cache changes to inventory slots on the client side to avoid recreating
  // components when we don't have to and the slots are just cleared by MC
  // temporarily.
  private lazy val pendingRemovalsActual = mutable.ArrayBuffer.fill(getContainerSize)(EmptyStack: StackOption)
  private lazy val pendingAddsActual = mutable.ArrayBuffer.fill(getContainerSize)(EmptyStack: StackOption)
  private var updateScheduled = false
  def pendingRemovals = {
    adjustSize(pendingRemovalsActual)
    pendingRemovalsActual
  }
  def pendingAdds = {
    adjustSize(pendingAddsActual)
    pendingAddsActual
  }

  private def adjustSize(buffer: mutable.ArrayBuffer[StackOption]): Unit = {
    val delta = buffer.length - getContainerSize
    if (delta > 0) {
      buffer.remove(buffer.length - delta, delta)
    }
    else if (delta < 0) {
      buffer.sizeHint(getContainerSize)
      for (i <- 0 until -delta) {
        buffer += EmptyStack
      }
    }
  }

  private def applyInventoryChanges(): Unit = {
    updateScheduled = false
    for (slot <- this.indices) {
      (pendingRemovals(slot), pendingAdds(slot)) match {
        case (SomeStack(removed), SomeStack(added)) =>
          if (!ItemStack.matches(removed, added)) {
            super.onItemRemoved(slot, removed)
            super.onItemAdded(slot, added)
            setChanged()
          } // else: No change, ignore.
        case (SomeStack(removed), EmptyStack) =>
          super.onItemRemoved(slot, removed)
          setChanged()
        case (EmptyStack, SomeStack(added)) =>
          super.onItemAdded(slot, added)
          setChanged()
        case _ => // No change.
      }

      pendingRemovals(slot) = EmptyStack
      pendingAdds(slot) = EmptyStack
    }
  }

  private def scheduleInventoryChange(): Unit = {
    if (!updateScheduled) {
      updateScheduled = true
      EventHandler.scheduleClient(() => applyInventoryChanges())
    }
  }

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    if (isServer) super.onItemAdded(slot, stack)
    else {
      pendingRemovals(slot) match {
        case SomeStack(removed) if ItemStack.matches(removed, stack) =>
          // Reverted to original state.
          pendingAdds(slot) = EmptyStack
          pendingRemovals(slot) = EmptyStack
        case _ =>
          // Got a removal and an add of *something else* in the same tick.
          pendingAdds(slot) = StackOption(stack)
          scheduleInventoryChange()
      }
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    if (isServer) super.onItemRemoved(slot, stack)
    else {
      pendingAdds(slot) match {
        case SomeStack(added) =>
          // If we have a pending add and get a remove on a slot it is
          // now either empty, or the previous remove is valid again.
          pendingAdds(slot) = EmptyStack
        case _ =>
          // If we have no pending add, only the first removal can be
          // relevant (further ones should in fact be impossible).
          if (pendingRemovals(slot).isEmpty) {
            pendingRemovals(slot) = StackOption(stack)
            scheduleInventoryChange()
          }
      }
    }
  }

  override protected def save(component: ManagedEnvironment, driver: DriverItem, stack: ItemStack): Unit = {
    if (isServer) {
      super.save(component, driver, stack)
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def initialize(): Unit = {
    super.initialize()
    if (isClient) {
      connectComponents()
    }
  }

  override def dispose(): Unit = {
    super.dispose()
    if (isClient) {
      disconnectComponents()
    }
  }

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      connectComponents()
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      disconnectComponents()
    }
  }

  override def saveForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    connectComponents()
    super.saveForClient(nbt, provider)
    saveData(nbt, provider)
  }

  override def loadForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForClient(nbt, provider)
    loadData(nbt, provider)
    connectComponents()
  }
}
