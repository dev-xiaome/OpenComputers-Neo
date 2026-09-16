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
 * ==两种宿主：物品形态与机架形态==
 * 1.7.10 的构造参数是 `(container, serverInventory, rack: Option[Rack], slot: Int)`：
 * `rack` 有值就表示「这台服务器插在机架里」，界面由此决定是否显示电源键，
 * 以及在「物品被移出机架」时自动关屏。1.21.1 里屏幕只能拿到容器，
 * 因此这两个值挂在容器的 [[rack]] / [[rackSlot]] 上（由 [[MenuTypes]] 的客户端工厂填）。
 *
 * ==降级说明==
 * 1.7.10 还会传入 `server: Option[li.cil.oc.server.component.Server]` 用来查询
 * `s.machine.isRunning`。`li.cil.oc.server.component` 尚未移植，容器层不能引用它，
 * 因此改成 [[isInRack]] + [[isRunningProvider]] 两个显式参数。
 *
 * TODO(server.component): `server/component` 移植完成后，把 `isRunningProvider` 的实参写成
 * `() => serverComponent.machine != null && serverComponent.machine.isRunning`。
 */
class Server(windowId: Int,
             playerInventory: Inventory,
             val serverInventory: ServerInventory,
             val isInRack: Boolean = false,
             val isRunningProvider: () => Boolean = () => false,
             val rack: Option[tileentity.Rack] = None,
             val rackSlot: Int = 0)
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

  /** 从客户端重建上下文构造（[[MenuTypes]] 的工厂用）。 */
  def this(ctx: MenuTypes.OpenContext,
           serverInventory: ServerInventory,
           isInRack: Boolean,
           isRunningProvider: () => Boolean,
           rack: Option[tileentity.Rack],
           rackSlot: Int) =
    this(ctx.windowId, ctx.inventory, serverInventory, isInRack, isRunningProvider, rack, rackSlot)

  override def stillValid(player: MCPlayer): Boolean = {
    // 机架形态：宿主在机架里，距离判定交给父类（通过 `otherInventory` 的距离语义）；
    // 物品形态：宿主就是玩家手里的那份堆叠，只要还是同一个玩家就有效。
    if (isInRack) super.stillValid(player)
    else player == playerInventory.player
  }

  var isRunning = false
  var isItem = true

  override def updateCustomData(nbt: CompoundTag): Unit = {
    super.updateCustomData(nbt)
    isRunning = nbt.getBoolean("isRunning")
    isItem = nbt.getBoolean("isItem")
  }

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    super.detectCustomDataChanges(nbt)
    if (isInRack) nbt.putBoolean("isRunning", isRunningProvider())
    else nbt.putBoolean("isItem", true)
  }
}
