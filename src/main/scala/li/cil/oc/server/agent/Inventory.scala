package li.cil.oc.server.agent

import li.cil.oc.api.internal
import li.cil.oc.util.{BlockPosition, InventoryUtils}
import net.minecraft.world.item.{Item, ItemStack}
import net.neoforged.neoforge.items.IItemHandler

/**
 * 机器人 / 无人机的「玩家物品栏」视图。
 *
 * ==1.7.10 → 1.21.1==
 *  - 1.7.10 里本类继承 `net.minecraft.entity.player.Inventory`，并被
 *    `server.agent.Player` 通过反射塞进假玩家的 `inventory` 字段。
 *    1.21.1 的 `Player#inventory` 是 **final 字段**（在构造期创建、不可替换），
 *    因此本类改为独立的 [[net.neoforged.neoforge.items.IItemHandler]] 实现：
 *    槽位 `[0, mainInventory.getSlots)` 映射到 `agent.mainInventory`，
 *    紧随其后的槽位映射到 `agent.equipmentInventory`（工具槽等）。
 *  - 1.7.10 用「负数槽位取反」(`~slot`) 编码装备槽的做法在 1.21.1 不再需要，
 *    统一改为顺序编码，避免出现非法槽位索引。
 *  - `IInventory` 的 `getSizeInventory/decrStackSize/setInventorySlotContents/...`
 *    分别对应 `IItemHandler` 的 `getSlots/extractItem/insertItem/...`。
 *
 * TODO(port): 上游把本类当作假玩家真正的手持物品栏（`player.inventory`），
 *  所以「使用物品 / 破坏方块时消耗物品」会直接作用在 agent 的物品栏上。
 *  现在假玩家用的是原版 `Inventory`，agent 物品栏与其是两份数据；
 *  1.21.1 由 [[Player.setInventoryPlayerItems]] / [[Player.detectInventoryPlayerChanges]]
 *  做「拷贝进 / 拷贝出」同步来近似原来的语义（详见 `Player.scala` 顶部的降级清单）。
 *
 * TODO(port): 上游覆写的 `decrementAnimations` / `writeToNBT` / `readFromNBT` /
 *  `armorItemInSlot` / `getTotalArmorValue` / `damageArmor` / `copyInventory` /
 *  `func_146030_a` / `changeCurrentItem` / `clearInventory` / `func_70439a` 等
 *  原版 `Inventory` 专有 API 在 1.21.1 已不存在（动画、护甲、NBT 由别处负责），
 *  机器人也没有护甲槽，故整体删除。
 *
 * TODO(port): `canHarvestBlock` / `getStrVsBlock` 依赖 1.7.10 的
 *  `Block#getMaterial` 与 `ItemStack#func_150998_b/func_150997_a`，
 *  1.21.1 改为 `BlockState` + `ItemStack#getDestroySpeed(BlockState)`（工具属性由
 *  `TieredItem` / 数据组件决定），本类不再提供这两个方法。
 */
class Inventory(val agent: internal.Agent) extends IItemHandler {
  private def mainHandler: IItemHandler = agent.mainInventory

  private def equipmentHandler: IItemHandler = agent.equipmentInventory

  private def mainSlots: Int = if (mainHandler == null) 0 else mainHandler.getSlots

  private def equipmentSlots: Int = if (equipmentHandler == null) 0 else equipmentHandler.getSlots

  /** 当前选中槽位的物品（对应 1.7.10 的 `selectedItemStack`）。 */
  def selectedItemStack: ItemStack = Inventory.getSlot(mainHandler, agent.selectedSlot)

  /** 工具槽物品（对应 1.7.10 的 `getCurrentItem`）。 */
  def getCurrentItem: ItemStack =
    if (equipmentSlots > 0) Inventory.getSlot(equipmentHandler, 0) else ItemStack.EMPTY

  /**
   * 主物品栏的槽位顺序：从当前选中槽位开始绕一圈
   * （对应 1.7.10 的 `inventorySlots`，插入物品时优先放进选中槽位）。
   */
  def inventorySlots: Seq[Int] =
    if (mainSlots <= 0) Seq.empty
    else {
      val selected = math.max(0, math.min(agent.selectedSlot, mainSlots - 1))
      (selected until mainSlots) ++ (0 until selected)
    }

  /** 主物品栏槽位数（对应 1.7.10 的 `getSizeInventory`）。 */
  def getSizeInventory: Int = mainSlots

  private def delegate(slot: Int): IItemHandler =
    if (slot < mainSlots) mainHandler else equipmentHandler

  private def localSlot(slot: Int): Int =
    if (slot < mainSlots) slot else slot - mainSlots

  // ----------------------------------------------------------------------- //
  // IItemHandler
  // ----------------------------------------------------------------------- //

  override def getSlots: Int = mainSlots + equipmentSlots

  override def getStackInSlot(slot: Int): ItemStack =
    if (slot < 0 || slot >= getSlots) ItemStack.EMPTY
    else Inventory.getSlot(delegate(slot), localSlot(slot))

  override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack =
    if (stack == null || stack.isEmpty || slot < 0 || slot >= getSlots) stack
    else delegate(slot).insertItem(localSlot(slot), stack, simulate)

  override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack =
    if (amount <= 0 || slot < 0 || slot >= getSlots) ItemStack.EMPTY
    else delegate(slot).extractItem(localSlot(slot), amount, simulate)

  override def getSlotLimit(slot: Int): Int =
    if (slot < 0 || slot >= getSlots) 0 else delegate(slot).getSlotLimit(localSlot(slot))

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    slot >= 0 && slot < getSlots && delegate(slot).isItemValid(localSlot(slot), stack)

  // ----------------------------------------------------------------------- //
  // 上游在原版 `Inventory` 上用到的辅助方法（1.21.1 无对应 API，这里自行实现）
  // ----------------------------------------------------------------------- //

  /** 对应 1.7.10 的 `consumeInventoryItem(item)`：从物品栏里吃掉一个同种物品。 */
  def consumeInventoryItem(item: Item): Boolean = {
    for (slot <- inventorySlots) {
      val stack = Inventory.getSlot(mainHandler, slot)
      if (!stack.isEmpty && stack.is(item)) {
        val extracted = mainHandler.extractItem(slot, 1, false)
        return extracted != null && !extracted.isEmpty
      }
    }
    false
  }

  /** 对应 1.7.10 的 `hasItem(item)`。 */
  def hasItem(item: Item): Boolean =
    (0 until mainSlots).exists { slot =>
      val stack = Inventory.getSlot(mainHandler, slot)
      !stack.isEmpty && stack.is(item)
    }

  /** 对应 1.7.10 的 `hasItemStack(stack)`（比较物品 + 数据组件）。 */
  def hasItemStack(stack: ItemStack): Boolean =
    stack != null && !stack.isEmpty && (0 until mainSlots).exists { slot =>
      val current = Inventory.getSlot(mainHandler, slot)
      !current.isEmpty && ItemStack.isSameItemSameComponents(current, stack)
    }

  /** 对应 1.7.10 的 `addItemStackToInventory(stack)`：优先塞进当前选中槽位。 */
  def addItemStackToInventory(stack: ItemStack): Boolean =
    InventoryUtils.insertIntoInventory(stack, mainHandler, slots = Option(inventorySlots))

  /** 对应 1.7.10 的 `dropAllItems()`。 */
  def dropAllItems(): Unit = InventoryUtils.dropAllSlots(BlockPosition(agent), mainHandler)
}

object Inventory {
  /** 读取 `IItemHandler` 的槽位，越界或 `null` 统一折算为 `ItemStack.EMPTY`。 */
  def getSlot(handler: IItemHandler, slot: Int): ItemStack = {
    if (handler == null || slot < 0 || slot >= handler.getSlots) return ItemStack.EMPTY
    val stack = handler.getStackInSlot(slot)
    if (stack == null) ItemStack.EMPTY else stack
  }

  /**
   * 用 `stack` 覆盖 `IItemHandler` 的某个槽位。
   *
   * TODO(port): 1.21.1 的 `IItemHandler` 没有 1.7.10 `setInventorySlotContents` 那样的
   *  「直接写槽位」语义，这里退化为「先抽出旧内容再插入新内容」。副作用是旧物品会被
   *  直接丢弃而不是走正常的移除流程（不会触发 `onDestroyed` 之类的钩子）。
   */
  def setSlot(handler: IItemHandler, slot: Int, stack: ItemStack): Unit = {
    if (handler == null || slot < 0 || slot >= handler.getSlots) return
    handler.extractItem(slot, handler.getSlotLimit(slot), false)
    if (stack != null && !stack.isEmpty) handler.insertItem(slot, stack, false)
  }
}
