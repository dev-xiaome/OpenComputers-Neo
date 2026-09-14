package li.cil.oc.common.container

import net.minecraft.world.entity.player.{Inventory, Player => MCPlayer}
import net.neoforged.neoforge.items.IItemHandler

/**
 * 平板电脑容器（原 1.7.10 `container.Tablet`）。
 *
 * ==降级说明==
 * 1.7.10 的第二个构造参数是 `li.cil.oc.common.item.TabletWrapper`，它同时提供
 * 「物品栏」与「当前组件槽位类型 / 等级」两件事。而 `TabletWrapper` 随
 * `common/item/Tablet.scala` 一起被降级掉了（它依赖 `common/inventory/ComponentInventory`、
 * `server/component/Tablet` 等尚未移植的包，见该文件的 TODO），因此这里把两件事拆成显式参数：
 *  - `tablet`：[[net.neoforged.neoforge.items.IItemHandler]]，即平板内部的物品栏；
 *  - `containerSlotType` / `containerSlotTier`：原来的
 *    `tablet.containerSlotType` / `tablet.containerSlotTier`。
 *
 * TODO(common.item): `TabletWrapper` 移植后，这里应改回
 * `class Tablet(windowId, playerInventory, tablet: TabletWrapper)`，并从 `tablet` 上读取槽位信息。
 */
class Tablet(windowId: Int,
             playerInventory: Inventory,
             val tablet: IItemHandler,
             val containerSlotType: String,
             val containerSlotTier: Int)
  extends Player(windowId, MenuTypes.Tablet.value(), playerInventory, tablet) {

  addSlotToContainer(new StaticComponentSlot(this, otherInventory, otherInventory.getSlots - 1,
    80, 35, containerSlotType, containerSlotTier))

  addPlayerInventorySlots(8, 84)

  override def stillValid(player: MCPlayer): Boolean = player == playerInventory.player
}
