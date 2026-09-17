package li.cil.oc.common.container

import li.cil.oc.common
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.{IItemHandler, SlotItemHandler}

/**
 * 槽位类型 / 等级固定的组件槽位（原 1.7.10 的 `StaticComponentSlot`）。
 *
 * 1.21.1 迁移要点：后端从 `IInventory` 换成 [[net.neoforged.neoforge.items.IItemHandler]]，
 * 因此统一继承 [[net.neoforged.neoforge.items.SlotItemHandler]]；
 * `setBackgroundIcon(IIcon)` → [[Slot#setBackground]]（贴图二元组）；
 * `getSlotStackLimit` → `getMaxStackSize`；
 * 原来的 `container` 参数改名为 `containerMenu`（原因见 [[ComponentSlot]]）。
 */
class StaticComponentSlot(val containerMenu: Player,
                          inventory: IItemHandler,
                          index: Int,
                          x: Int,
                          y: Int,
                          val slot: String,
                          val tier: Int)
  extends SlotItemHandler(inventory, index, x, y) with ComponentSlot {

  setBackground(SlotIcons.atlas, SlotIcons.get(slot))

  val tierIcon: ResourceLocation = SlotIcons.get(tier)

  /**
   * 原 `getSlotStackLimit`。
   *
   * 工具 / 任意 / 过滤槽沿用后端物品栏的容量（`SlotItemHandler#getMaxStackSize`），
   * `Slot.None` 表示「不接受的槽位」所以给 0，其余专用槽位（卡 / CPU / 内存 …）一律 1。
   */
  override def getMaxStackSize: Int = slot match {
    case common.Slot.Tool | common.Slot.Any | common.Slot.Filtered => super.getMaxStackSize
    case common.Slot.None => 0
    case _ => 1
  }
}
