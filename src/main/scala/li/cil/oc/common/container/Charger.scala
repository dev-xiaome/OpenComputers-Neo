package li.cil.oc.common.container

import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Inventory

/**
 * 充电器容器（原 1.7.10 `container.Charger`）。
 *
 * 1.21.1 迁移：`Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 * 宿主的 `IInventory` 由 `tileentity.Charger` 自身充当（实现了 `IItemHandler`）。
 */
class Charger(windowId: Int, playerInventory: Inventory, val charger: tileentity.Charger)
  extends Player(windowId, MenuTypes.Charger.value(), playerInventory, charger) {

  /** 从客户端重建上下文构造（[[MenuTypes]] 的工厂用），见 [[Adapter]] 的同名构造器。 */
  def this(ctx: MenuTypes.OpenContext, charger: tileentity.Charger) =
    this(ctx.windowId, ctx.inventory, charger)

  addSlotToContainer(80, 35, "tablet")
  addPlayerInventorySlots(8, 84)
}
