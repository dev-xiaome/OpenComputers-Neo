package li.cil.oc.common.container

import com.mojang.datafixers.util.Pair

import li.cil.oc.{api, common}
import li.cil.oc.common.tileentity
import li.cil.oc.util.SideTracker
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 机器人容器（原 1.7.10 `container.Robot`）。
 *
 * ==1.21.1 迁移要点==
 *  - `Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 *  - 物品栏后端 `IInventory` → [[net.neoforged.neoforge.items.IItemHandler]]；
 *    `getSizeInventory` → `getSlots`；
 *  - 组件环境数组在 1.21.1 里叫 `componentEnvironments`（原名 `components` 与
 *    `TileEntity#components` 冲突，见 `common/inventory/ComponentInventory`）；
 *  - **进度条改用 `DataSlot`**：1.7.10 覆写 `ICrafting#updateProgressBar(id, value)`（客户端）+
 *    `sendProgressBarUpdate(id, value)`（服务端）；1.21.1 的服务端写值仍然走
 *    [[Player.sendProgressBarUpdate]]，客户端则回调
 *    [[net.minecraft.world.inventory.AbstractContainerMenu#setData]]，所以这里覆写 `setData`。
 *    槽位 id 由 [[Player.addProgressBarSlot]] 动态分配（原来是写死的 0 / 1）；
 *  - `func_111238_b()` → `isActive`；`getBackgroundIconIndex` → `getNoItemIcon`；
 *    `getStack` 越界时返回 `ItemStack.EMPTY`（1.7.10 返回 `null`，1.21.1 的槽位协议不接受 `null`）；
 *  - `@SideOnly(Dist.CLIENT)` 删除。
 */
class Robot(windowId: Int, playerInventory: Inventory, val robot: tileentity.Robot)
  extends Player(windowId, MenuTypes.Robot.value(), playerInventory, robot) {

  val hasScreen = robot.componentEnvironments.exists {
    case Some(_: api.internal.TextBuffer) => true
    case _ => false
  }

  /** 从客户端重建上下文构造（[[MenuTypes]] 的工厂用），见 [[Adapter]] 的同名构造器。 */

  private val withScreenHeight = 256
  private val noScreenHeight = 108
  val deltaY = if (hasScreen) 0 else withScreenHeight - noScreenHeight

  addSlotToContainer(170 + 0 * slotSize, 232 - deltaY, common.Slot.Tool)
  addSlotToContainer(170 + 1 * slotSize, 232 - deltaY, robot.containerSlotType(1), robot.containerSlotTier(1))
  addSlotToContainer(170 + 2 * slotSize, 232 - deltaY, robot.containerSlotType(2), robot.containerSlotTier(2))
  addSlotToContainer(170 + 3 * slotSize, 232 - deltaY, robot.containerSlotType(3), robot.containerSlotTier(3))

  for (i <- 0 to 3) {
    val y = 156 + i * slotSize - deltaY
    for (j <- 0 to 3) {
      val x = 170 + j * slotSize
      addSlot(new InventorySlot(this, otherInventory, slots.size, x, y))
    }
  }
  for (i <- 16 until 64) {
    addSlot(new InventorySlot(this, otherInventory, slots.size, -10000, -10000))
  }

  addPlayerInventorySlots(6, 174 - deltaY)

  // This factor is used to make the energy values transferable using
  // MCs 'progress bar' stuff, even though those internally send the
  // values as shorts over the net (for whatever reason).
  private val factor = 100

  private var lastSentBuffer = -1

  private var lastSentBufferSize = -1

  /** 进度条 0：内部能量缓冲（原 `sendProgressBarUpdate(0, ...)`）。 */
  private val bufferId = addProgressBarSlot()

  /** 进度条 1：缓冲上限（原 `sendProgressBarUpdate(1, ...)`）。 */
  private val bufferSizeId = addProgressBarSlot()

  /** 原 `updateProgressBar`（只会在客户端被调用；1.21.1 的入口是 `setData`）。 */
  override def setData(id: Int, value: Int): Unit = {
    super.setData(id, value)
    if (id == bufferId) {
      robot.globalBuffer = value * factor
    }

    if (id == bufferSizeId) {
      robot.globalBufferSize = value * factor
    }
  }

  override def broadcastChanges(): Unit = {
    super.broadcastChanges()
    if (SideTracker.isServer) {
      val currentBuffer = robot.globalBuffer.toInt / factor
      if (currentBuffer != lastSentBuffer) {
        lastSentBuffer = currentBuffer
        sendProgressBarUpdate(bufferId, lastSentBuffer)
      }

      val currentBufferSize = robot.globalBufferSize.toInt / factor
      if (currentBufferSize != lastSentBufferSize) {
        lastSentBufferSize = currentBufferSize
        sendProgressBarUpdate(bufferSizeId, lastSentBufferSize)
      }
    }
  }

  class InventorySlot(containerMenu: Player, inventory: IItemHandler, index: Int, x: Int, y: Int)
    extends StaticComponentSlot(containerMenu, inventory, index, x, y, common.Slot.Any, common.Tier.Any) {

    def isValid: Boolean = robot.isInventorySlot(getSlotIndex)

    override def isActive: Boolean = isValid && super.isActive

    override def getNoItemIcon: Pair[ResourceLocation, ResourceLocation] =
      if (isValid) super.getNoItemIcon
      else SlotIcons.background(common.Tier.None)

    override def getItem: ItemStack =
      if (isValid) super.getItem
      else ItemStack.EMPTY
  }
}
