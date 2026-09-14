package li.cil.oc.common.container

import com.mojang.datafixers.util.Pair

import li.cil.oc.common
import li.cil.oc.common.InventorySlots.InventorySlot
import li.cil.oc.util.{InventoryUtils, SideTracker}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.{Player => MCPlayer}
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.{IItemHandler, SlotItemHandler}

/**
 * 槽位类型 / 等级由「当前模板」动态决定的组件槽位（原 `DynamicComponentSlot`），
 * 用于装配机这类会随放入的模板改变槽位构成的容器。
 *
 * 1.21.1 迁移要点同 [[ComponentSlot]]；另外：
 *  - `getBackgroundIconIndex` → `getNoItemIcon`（返回贴图二元组）；
 *  - `putStack(null)` → `set(ItemStack.EMPTY)`；
 *  - `getHasStack` → `hasItem`、`getStack` → `getItem`、`isItemValid` → `mayPlace`；
 *  - 原来的 `container` 参数改名为 `containerMenu`（原因见 [[ComponentSlot]]）。
 */
class DynamicComponentSlot(val containerMenu: Player,
                           inventory: IItemHandler,
                           index: Int,
                           x: Int,
                           y: Int,
                           val info: DynamicComponentSlot => InventorySlot,
                           val containerTierGetter: () => Int)
  extends SlotItemHandler(inventory, index, x, y) with ComponentSlot {

  override def tier: Int = {
    val mainTier = containerTierGetter()
    if (mainTier >= 0) info(this).tier
    else mainTier
  }

  def tierIcon: ResourceLocation = SlotIcons.get(tier)

  def slot: String = {
    val mainTier = containerTierGetter()
    if (mainTier >= 0) info(this).slot
    else common.Slot.None
  }

  override def getNoItemIcon: Pair[ResourceLocation, ResourceLocation] = SlotIcons.background(slot)

  override def getMaxStackSize: Int = slot match {
    case common.Slot.Tool | common.Slot.Any | common.Slot.Filtered => super.getMaxStackSize
    case common.Slot.None => 0
    case _ => 1
  }

  override protected def clearIfInvalid(player: MCPlayer): Unit = {
    if (SideTracker.isServer && hasItem && !mayPlace(getItem)) {
      val stack = getItem
      set(ItemStack.EMPTY)
      InventoryUtils.addToPlayerInventory(stack, player)
    }
  }
}
