package li.cil.oc.common.container

import li.cil.oc.common.InventorySlots
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.common.tileentity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.{Inventory, Player => MCPlayer}

/**
 * 服务器容器（原 1.7.10 `container.Server`）。
 *
 * 1.21.1 迁移要点：
 *  - `Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 *  - `getInventory.size` → `slots.size`；`canInteractWith` → `stillValid`。
 *
 * ==降级说明==
 * 1.7.10 的构造参数是 `server: Option[li.cil.oc.server.component.Server]`，
 * 用来查询 `s.machine.isRunning`。`li.cil.oc.server.component` 尚未移植
 * （当前编译集里只有 `server/component/FileSystem.scala`），容器层不能引用它，因此：
 *  - `server` 的存在性保留下来（类型放宽为 `Option[AnyRef]`，用于 [[stillValid]] 分支），
 *    真正的服务端组件对象仍可由调用方原样传入；
 *  - 「机器是否在运行」改由 [[isRunningProvider]] 回调提供。
 *
 * TODO(server.component): `server/component` 移植完成后，把 `isRunningProvider` 的实参写成
 * `() => serverComponent.machine.isRunning`（或在容器里直接持有 `server.component.Server`）。
 */
class Server(windowId: Int,
             playerInventory: Inventory,
             val serverInventory: ServerInventory,
             val server: Option[AnyRef] = None,
             val isRunningProvider: () => Boolean = () => false)
  extends Player(windowId, MenuTypes.Server.value(), playerInventory, serverInventory) {

  for (i <- 0 to 1) {
    val slot = InventorySlots.server(serverInventory.tier)(slots.size)
    addSlotToContainer(76, 7 + i * slotSize, slot.slot, slot.tier)
  }

  val verticalSlots = math.min(3, 1 + serverInventory.tier)
  for (i <- 0 to verticalSlots) {
    val slot = InventorySlots.server(serverInventory.tier)(slots.size)
    addSlotToContainer(100, 7 + i * slotSize, slot.slot, slot.tier)
  }

  for (i <- 0 to verticalSlots) {
    val slot = InventorySlots.server(serverInventory.tier)(slots.size)
    addSlotToContainer(124, 7 + i * slotSize, slot.slot, slot.tier)
  }

  for (i <- 0 to verticalSlots) {
    val slot = InventorySlots.server(serverInventory.tier)(slots.size)
    addSlotToContainer(148, 7 + i * slotSize, slot.slot, slot.tier)
  }

  for (i <- 2 to verticalSlots) {
    val slot = InventorySlots.server(serverInventory.tier)(slots.size)
    addSlotToContainer(76, 7 + i * slotSize, slot.slot, slot.tier)
  }

  {
    val slot = InventorySlots.server(serverInventory.tier)(slots.size)
    addSlotToContainer(26, 34, slot.slot, slot.tier)
  }

  // Show the player's inventory.
  addPlayerInventorySlots(8, 84)

  override def stillValid(player: MCPlayer): Boolean = {
    if (server.isDefined) super.stillValid(player)
    else player == playerInventory.player
  }

  var isRunning = false
  var isItem = true

  /**
   * 服务端所在的机架与槽位（物品形态时为 `None` / 0）。
   *
   * 1.7.10 里这两个值是屏幕的构造参数（`client.gui.Server(playerInventory, serverInventory, rack, slot)`）；
   * 1.21.1 的屏幕只能拿到容器，因此改由 [[MenuTypes]] 的客户端工厂在重建容器时
   * 从载荷里读出来挂到这里，"物品被从机架里取走就关屏" 与 "点电源键" 两条逻辑
   * 都从容器上取。
   */
  var rack: Option[tileentity.Rack] = None

  var rackSlot: Int = 0

  /** 服务端组件（[[li.cil.oc.server.component.Server]]）尚未移植，见类注释的降级说明。 */
  def hasServer: Boolean = server.isDefined

  override def updateCustomData(nbt: CompoundTag): Unit = {
    super.updateCustomData(nbt)
    isRunning = nbt.getBoolean("isRunning")
    isItem = nbt.getBoolean("isItem")
  }

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    super.detectCustomDataChanges(nbt)
    if (server.isDefined) nbt.putBoolean("isRunning", isRunningProvider())
    else nbt.putBoolean("isItem", true)
  }
}
