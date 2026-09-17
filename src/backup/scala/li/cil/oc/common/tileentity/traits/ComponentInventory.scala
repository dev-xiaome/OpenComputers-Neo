package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.driver.Item
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.common.EventHandler
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

  /**
   * 精确对应 OCCE 的 `StackOption`：空栈（`null` 或 `ItemStack.EMPTY`）一律映射为 `None`，
   * 而不是 `Some(ItemStack.EMPTY)`，否则「槽位已空」会被误判为「有一个待处理的栈」。
   */
  private def stackOption(stack: ItemStack): Option[ItemStack] =
    if (stack == null || stack.isEmpty) None else Some(stack)

  private def applyInventoryChanges(): Unit = {
    updateScheduled = false
    for (slot <- 0 until getSlots) {
      (pendingRemovals(slot), pendingAdds(slot)) match {
        case (Some(removed), Some(added)) =>
          // 1.21.1 的物品组件（components）同时涵盖旧版的 damage 与 NBT，因此一次比较即可。
          if (!ItemStack.isSameItemSameComponents(removed, added)) {
            super.onItemRemoved(slot, removed)
            super.onItemAdded(slot, added)
            setChanged()
          } // else: No change, ignore.
        case (Some(removed), None) =>
          super.onItemRemoved(slot, removed)
          setChanged()
        case (None, Some(added)) =>
          super.onItemAdded(slot, added)
          setChanged()
        case _ => // No change.
      }

      pendingRemovals(slot) = None
      pendingAdds(slot) = None
    }
  }

  private def scheduleInventoryChange(): Unit = {
    if (!updateScheduled) {
      updateScheduled = true
      // 把变更合并到本 tick 结束后的客户端任务里执行，避免 MC 临时清空槽位时
      // 反复销毁 / 重建组件（对齐 OCCE 的 EventHandler.scheduleClient）。
      EventHandler.scheduleClient(() => applyInventoryChanges())
    }
  }

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    if (isServer) super.onItemAdded(slot, stack)
    else {
      pendingRemovals(slot) match {
        case Some(removed) if ItemStack.isSameItemSameComponents(removed, stack) =>
          // 本 tick 内先删后加、且内容与原先完全一致：槽位其实是原样，撤销这对变更。
          pendingAdds(slot) = None
          pendingRemovals(slot) = None
        case _ =>
          // 本 tick 内出现了「移除后又加入了别的东西」。
          pendingAdds(slot) = stackOption(stack)
          scheduleInventoryChange()
      }
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    if (isServer) super.onItemRemoved(slot, stack)
    else {
      pendingAdds(slot) match {
        case Some(_) =>
          // 已有待处理的「添加」，此时出现「移除」意味着这次添加被抵消。
          pendingAdds(slot) = None
        case _ =>
          // 没有待处理的添加时，只有第一次移除是有效的（后续移除理论上不可能出现）。
          if (pendingRemovals(slot).isEmpty) {
            pendingRemovals(slot) = stackOption(stack)
            scheduleInventoryChange()
          }
      }
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
