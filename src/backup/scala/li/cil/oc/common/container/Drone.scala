package li.cil.oc.common.container

import com.mojang.datafixers.util.Pair

import li.cil.oc.common
import li.cil.oc.common.entity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 无人机容器（原 1.7.10 `container.Drone`）。
 *
 * 1.21.1 迁移要点：
 *  - `Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 *  - 槽位后端 `IInventory` → [[net.neoforged.neoforge.items.IItemHandler]]（`drone.mainInventory`）；
 *  - `getSizeInventory` → `getSlots`；`func_111238_b()` → `isActive`；
 *    `getBackgroundIconIndex` → `getNoItemIcon`；`getStack` 越界时返回 `ItemStack.EMPTY`
 *    （1.7.10 返回 `null`，1.21.1 的槽位协议里不允许 `null`）。
 *
 * TODO(common.entity): `common/entity/Drone.scala` 尚未移植（仍混有 1.7.10 的 `getSizeInventory`
 * 等旧方法），这里只依赖它的 `mainInventory`；等实体层移植完成后不需要改动本文件。
 */
class Drone(windowId: Int, playerInventory: Inventory, val drone: entity.Drone)
  extends Player(windowId, MenuTypes.Drone.value(), playerInventory, drone.mainInventory) {

  val deltaY = 0

  /** 从客户端重建上下文构造（[[MenuTypes]] 的工厂用），见 [[Adapter]] 的同名构造器。 */

  for (i <- 0 to 1) {
    val y = 8 + i * slotSize - deltaY
    for (j <- 0 to 3) {
      val x = 98 + j * slotSize
      addSlot(new InventorySlot(this, otherInventory, slots.size, x, y))
    }
  }

  addPlayerInventorySlots(8, 66)

  class InventorySlot(containerMenu: Player, inventory: IItemHandler, index: Int, x: Int, y: Int)
    extends StaticComponentSlot(containerMenu, inventory, index, x, y, common.Slot.Any, common.Tier.Any) {

    def isValid: Boolean = (0 until drone.mainInventory.getSlots).contains(getSlotIndex)

    override def isActive: Boolean = isValid && super.isActive

    override def getNoItemIcon: Pair[ResourceLocation, ResourceLocation] =
      if (isValid) super.getNoItemIcon
      else SlotIcons.background(common.Tier.None)

    override def getItem: ItemStack =
      if (isValid) super.getItem
      else ItemStack.EMPTY
  }
}
