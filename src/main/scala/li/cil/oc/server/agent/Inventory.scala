package li.cil.oc.server.agent

import li.cil.oc.api.internal
import li.cil.oc.util.{BlockPosition, InventoryUtils}
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.IItemHandler

/**
 * 机器人 / 无人机的「玩家物品栏」视图。
 *
 * ==1.7.10 与 OCCE 到 1.21.1==
 *  - 1.7.10 与 OCCE 里本类都继承原版 `InventoryPlayer` / `PlayerInventory`，并被
 *    `server.agent.Player` 写进假玩家的 `inventory` 字段（1.7.10 直接赋值，OCCE 直接赋值，
 *    BattleGear2 兼容分支才走反射），于是「假玩家的物品栏」和「agent 的物品栏」是同一份数据。
 *  - 1.21.1 的 `Player#inventory` 是 **final 字段**（`javap` 可见），既不能赋值也不能用反射
 *    合法写入，因此本类改为独立的 [[net.neoforged.neoforge.items.IItemHandler]] 视图：
 *    槽位 `[0, mainHandler.getSlots)` 映射到 `agent.mainInventory`，紧随其后的槽位映射到
 *    `agent.equipmentInventory`（工具槽等）。
 *  - `IInventory` 的 `getSizeInventory` / `decrStackSize` / `setInventorySlotContents` 等分别对应
 *    `IItemHandler` 的 `getSlots` / `extractItem` / `insertItem` 等。
 *
 * 为了让 [[Player]] 能像基准那样查询「当前工具」，这里保留了上游 `Inventory` 的几个查询入口：
 *  - `getCurrentItem`：1.7.10 的 `getCurrentItem`，也是 OCCE 的 `getSelected`。
 *  - `getFreeSlot`：1.7.10 的 `getFirstEmptyStack`，也是 OCCE 的 `getFreeSlot`。
 *  - `getDestroySpeed` / `canHarvestBlock`：1.7.10 的 `getStrVsBlock` / `canHarvestBlock`、
 *    OCCE 的 `getDestroySpeed`。
 *
 * 以下上游覆写在本移植中没有对应物，整体删除：`decrementAnimations` / `writeToNBT` /
 *  `readFromNBT` / `armorItemInSlot` / `getTotalArmorValue` / `damageArmor` / `copyInventory` /
 *  `func_146030_a` / `changeCurrentItem` / `clearInventory` / `func_70439a`。动画、护甲与物品栏
 *  落盘在 1.21.1 分别由实体、附魔与数据组件负责，而机器人本来也没有护甲槽。
 *
 * 唯一的另一处降级是 OCCE 覆写的 `tick()`（物品栏心跳，给槽内物品调用 `inventoryTick`）：
 *  NeoForge 的 `FakePlayer#tick()` 是空实现，假玩家也不会被加入服务端的实体 tick 列表，
 *  所以 1.21.1 里不存在「原版会调用 `player.inventory.tick()`」的时机；在别处强行调用会
 *  改变物品的更新频率，因此不做。
 */
class Inventory(val agent: internal.Agent) extends IItemHandler {
  private def mainHandler: IItemHandler = agent.mainInventory

  private def equipmentHandler: IItemHandler = agent.equipmentInventory

  private def mainSlots: Int = if (mainHandler == null) 0 else mainHandler.getSlots

  private def equipmentSlots: Int = if (equipmentHandler == null) 0 else equipmentHandler.getSlots

  /** 当前选中槽位的物品（对应 1.7.10 的 `selectedItemStack`）。 */
  def selectedItemStack: ItemStack = Inventory.getSlot(mainHandler, agent.selectedSlot)

  /** 工具槽物品（对应 1.7.10 的 `getCurrentItem`、OCCE 的 `getSelected`）。 */
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

  /** 第一个空闲槽位；完全没有空位时返回 `-1`（对应 1.7.10 的 `getFirstEmptyStack`、OCCE 的 `getFreeSlot`）。 */
  def getFreeSlot: Int =
    if (selectedItemStack.isEmpty) agent.selectedSlot
    else inventorySlots.find(slot => Inventory.getSlot(mainHandler, slot).isEmpty).getOrElse(-1)

  /**
   * 工具槽物品对指定方块的挖掘速度（对应 1.7.10 的 `getStrVsBlock`、OCCE 的 `getDestroySpeed`）。
   * 空手时按原版惯例返回 `1`。
   */
  def getDestroySpeed(state: BlockState): Float = {
    val held = getCurrentItem
    if (held.isEmpty) 1f else held.getDestroySpeed(state)
  }

  /**
   * 工具槽物品能否采集指定方块（对应 1.7.10 的 `canHarvestBlock`）。
   *
   * 1.7.10 的判定是「方块材料的工具不是必需的，或手上的工具够格」；1.21.1 里前半部分由
   * `BlockState#requiresCorrectToolForDrops` 表达，后半部分由 `ItemStack#isCorrectToolForDrops` 判定。
   */
  def canHarvestBlock(state: BlockState): Boolean =
    if (!state.requiresCorrectToolForDrops) true
    else {
      val held = getCurrentItem
      !held.isEmpty && held.isCorrectToolForDrops(state)
    }

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
  // 上游在原版 `Inventory` 上用到的辅助方法（1.21.1 没有对应 API，这里按相同语义实现）
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

  /** 对应 1.7.10 的 `hasItemStack(stack)`（比较物品与数据组件）。 */
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
   * 1.21.1 的 `IItemHandler` 没有 1.7.10 `setInventorySlotContents` 那样「直接写槽位」的语义，
   * 这里退化为「先抽出旧内容再插入新内容」。副作用是旧物品会被直接丢弃而不是走正常的移除流程
   * （不会触发 `onDestroyed` 之类的钩子）。
   */
  def setSlot(handler: IItemHandler, slot: Int, stack: ItemStack): Unit = {
    if (handler == null || slot < 0 || slot >= handler.getSlots) return
    handler.extractItem(slot, handler.getSlotLimit(slot), false)
    if (stack != null && !stack.isEmpty) handler.insertItem(slot, stack, false)
  }
}
