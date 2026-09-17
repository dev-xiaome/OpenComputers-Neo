package li.cil.oc.common.container

import li.cil.oc.common
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.{Player => MCPlayer}
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.SlotItemHandler

/**
 * OC 组件槽位的公共部分（原 1.7.10 的 `trait ComponentSlot extends Slot`）。
 *
 * ==1.21.1 迁移要点==
 *  - `Slot` → [[net.neoforged.neoforge.items.SlotItemHandler]]：槽位后端从 `IInventory`
 *    换成 `IItemHandler`，槽位里的物品读写都由后者负责。
 *  - `func_111238_b()`（1.7.10 的 `isEnabled`）→ [[net.minecraft.world.inventory.Slot#isActive]]。
 *  - `isItemValid` → `mayPlace`；`getSlotStackLimit` → `getMaxStackSize`；
 *    `onPickupFromSlot` → `onTake`；`putStack` → `set`；`onSlotChanged` → `setChanged`；
 *    `decrStackSize` → `remove`；`getHasStack` → `hasItem`；`getStack` → `getItem`；
 *    `getBackgroundIconIndex` → `getNoItemIcon`（返回值也从 `IIcon` 变成贴图二元组）。
 *  - `slot.inventory`（`IInventory`）不再存在：`SlotItemHandler` 通过
 *    `getItemHandler` 暴露后端，`Slot#container` 恒为 `EmptyContainer`，
 *    因此凡是需要「判断两个槽位是不是同一个物品栏」的地方都改用
 *    [[Player.slotInventory]]。
 *  - **宿主容器成员改名为 [[containerMenu]]**：1.21.1 的 `Slot` 有一个公开字段
 *    `container`（`net.minecraft.world.Container`），Scala 里继承来的具体字段会盖住
 *    trait 里的抽象 `def container`，所以不能再沿用 1.7.10 的 `def container`。
 *  - `@SideOnly(Dist.CLIENT)` 一律删除（NeoForge 会抛异常）：`isActive` / `getNoItemIcon`
 *    本来就只在客户端渲染时被调用。
 */
trait ComponentSlot extends SlotItemHandler {
  /** 本槽位所属的 OC 容器（原名 `container`，改动原因见类注释）。 */
  def containerMenu: Player

  def slot: String

  def tier: Int

  /** 槽位等级图标（原 `IIcon`；1.21.1 改为贴图位置，见 [[SlotIcons]]）。 */
  def tierIcon: ResourceLocation

  var changeListener: Option[net.minecraft.world.inventory.Slot => Unit] = None

  // ----------------------------------------------------------------------- //

  override def isActive: Boolean = slot != common.Slot.None && tier != common.Tier.None && super.isActive

  override def mayPlace(stack: ItemStack): Boolean = getItemHandler.isItemValid(getSlotIndex, stack)

  override def onTake(player: MCPlayer, stack: ItemStack): Unit = {
    super.onTake(player, stack)
    for (other <- containerMenu.inventorySlots) other match {
      case dynamic: ComponentSlot => dynamic.clearIfInvalid(player)
      case _ =>
    }
  }

  override def set(stack: ItemStack): Unit = {
    super.set(stack)
    getItemHandler match {
      case playerAware: common.tileentity.traits.PlayerInputAware =>
        playerAware.onSetInventorySlotContents(containerMenu.playerInventory.player, getSlotIndex, stack)
      case _ =>
    }
  }

  override def setChanged(): Unit = {
    super.setChanged()
    // 1.7.10 的 `Slot#onSlotChanged` 会顺手 `inventory.markDirty()`；`SlotItemHandler`
    // 的 `container` 是 `EmptyContainer`，`super.setChanged()` 实际是空操作，这里补回来。
    getItemHandler match {
      case inventory: common.inventory.SimpleInventory => inventory.markDirty()
      case _ =>
    }
    for (other <- containerMenu.inventorySlots) other match {
      case dynamic: ComponentSlot => dynamic.clearIfInvalid(containerMenu.playerInventory.player)
      case _ =>
    }
    changeListener.foreach(_(this))
  }

  protected def clearIfInvalid(player: MCPlayer): Unit = {}
}
