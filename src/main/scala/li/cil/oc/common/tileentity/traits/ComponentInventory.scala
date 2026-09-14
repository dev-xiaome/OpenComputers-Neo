package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.driver.Item
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.common.inventory
import li.cil.oc.common.tileentity.ItemHandlerProvider
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

import scala.collection.mutable

/**
 * 带「组件槽位」的方块实体：槽位里的物品通过 `li.cil.oc.api.Driver` 变成
 * `ManagedEnvironment` 并挂到本体的节点上
 * （对应 1.7.10 的 `common.tileentity.traits.ComponentInventory`）。
 *
 * 1.21.1 迁移要点：
 *  - `getSizeInventory` → `getSlots`。
 *  - `@SideOnly(Side.CLIENT)` 全部删除（NeoForge 会抛异常）。
 *  - 客户端「延迟到下一个 tick 再应用物品栏变更」的调度器
 *    `li.cil.oc.common.EventHandler.scheduleClient` 尚未移植，见 [[scheduleInventoryChange]]。
 *  - `removed.isItemEqual(added) && ItemStack.areItemStackTagsEqual(...)` 合并为
 *    `ItemStack.isSameItemSameComponents`（1.21.1 的物品组件已经涵盖旧版的 damage + NBT）。
 *  - 混入 [[li.cil.oc.common.tileentity.ItemHandlerProvider]]，把本方块实体暴露成
 *    NeoForge 的 `Capabilities.ItemHandler.BLOCK` 提供者（组件槽位也能被漏斗 / 管道访问）。
 */
trait ComponentInventory extends Environment with Inventory with inventory.ComponentInventory with ItemHandlerProvider {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  override def host = this

  // ----------------------------------------------------------------------- //

  // Cache changes to inventory slots on the client side to avoid recreating
  // components when we don't have to and the slots are just cleared by MC
  // temporarily.
  private lazy val pendingRemovalsActual = mutable.ArrayBuffer.fill(getSlots)(None: Option[ItemStack])
  private lazy val pendingAddsActual = mutable.ArrayBuffer.fill(getSlots)(None: Option[ItemStack])
  private var updateScheduled = false
  def pendingRemovals: mutable.ArrayBuffer[Option[ItemStack]] = {
    adjustSize(pendingRemovalsActual)
    pendingRemovalsActual
  }
  def pendingAdds: mutable.ArrayBuffer[Option[ItemStack]] = {
    adjustSize(pendingAddsActual)
    pendingAddsActual
  }

  private def adjustSize[T](buffer: mutable.ArrayBuffer[Option[T]]): Unit = {
    val delta = buffer.length - getSlots
    if (delta > 0) {
      buffer.remove(buffer.length - delta, delta)
    }
    else if (delta < 0) {
      buffer.sizeHint(getSlots)
      for (i <- 0 until -delta) {
        buffer += None
      }
    }
  }

  private def applyInventoryChanges(): Unit = {
    updateScheduled = false
    for (slot <- 0 until getSlots) {
      (pendingRemovals(slot), pendingAdds(slot)) match {
        case (Some(removed), Some(added)) =>
          // 1.21.1 的物品组件（components）同时涵盖旧版的 damage 与 NBT，因此一次比较即可。
          if (!ItemStack.isSameItemSameComponents(removed, added)) {
            super.onItemRemoved(slot, removed)
            super.onItemAdded(slot, added)
          } // else: No change, ignore.
        case (Some(removed), None) =>
          super.onItemRemoved(slot, removed)
        case (None, Some(added)) =>
          super.onItemAdded(slot, added)
        case _ => // No change.
      }

      pendingRemovals(slot) = None
      pendingAdds(slot) = None
    }
  }

  private def scheduleInventoryChange(): Unit = {
    if (!updateScheduled) {
      updateScheduled = true
      // 原实现：EventHandler.scheduleClient(() => applyInventoryChanges())，
      // 把这些变更合并到本 tick 结束后的客户端任务里执行，避免 MC 临时清空槽位时
      // 反复销毁 / 重建组件。
      // TODO(common.EventHandler): 客户端延迟调度器尚未移植，这里直接同步执行；
      // 移植后应改回 EventHandler.scheduleClient(() => applyInventoryChanges())。
      applyInventoryChanges()
    }
  }

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    if (isServer) super.onItemAdded(slot, stack)
    else {
      pendingAdds(slot) = Option(stack)
      scheduleInventoryChange()
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    if (isServer) super.onItemRemoved(slot, stack)
    else if (pendingRemovals(slot).isEmpty) {
      pendingRemovals(slot) = Option(stack)
      scheduleInventoryChange()
    }
  }

  override protected def save(component: ManagedEnvironment, driver: Item, stack: ItemStack): Unit = {
    if (isServer) {
      super.save(component, driver, stack)
    }
  }

  // ----------------------------------------------------------------------- //

  override def initialize(): Unit = {
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

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    connectComponents()
    super.writeToNBTForClient(nbt)
    save(nbt)
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    load(nbt)
    connectComponents()
  }
}
