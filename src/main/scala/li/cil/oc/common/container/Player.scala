package li.cil.oc.common.container

import li.cil.oc.common
import li.cil.oc.common.InventorySlots.InventorySlot
import li.cil.oc.common.Tier
import li.cil.oc.util.SideTracker
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.{Inventory, Player => MCPlayer}
import net.minecraft.world.inventory.{AbstractContainerMenu, ClickType, ContainerListener, DataSlot, MenuType, Slot}
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.items.{IItemHandler, SlotItemHandler}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * OC 全部容器的基类（原 1.7.10 的 `li.cil.oc.common.container.Player`）。
 *
 * ==1.21.1 迁移要点==
 *  - `net.minecraft.inventory.Container` → [[net.minecraft.world.inventory.AbstractContainerMenu]]；
 *    构造器必须显式传入 `MenuType` 与 `containerId`，因此**每个容器构造器的第一个参数都是 `windowId`**
 *    （由 [[MenuOpening]] / `MenuProvider` 分配）。
 *  - `IInventory` → [[net.neoforged.neoforge.items.IItemHandler]]，槽位统一用
 *    [[net.neoforged.neoforge.items.SlotItemHandler]] 承载（见 [[ComponentSlot]]）；
 *    旧接口里的 `getSizeInventory` → `getSlots`，`isItemValidForSlot` → `isItemValid(slot, stack)`。
 *  - `inventorySlots`（`List<Slot>`）→ `slots`（`NonNullList<Slot>`）。这里包了一层
 *    [[inventorySlots]] 转成 Scala 的 `IndexedSeq[Slot]`，子类写法基本不用改。
 *  - `canInteractWith(player)` → `stillValid(player)`；`slotClick(...)` → `clicked(..., ClickType, ...)`；
 *    `detectAndSendChanges()` → `broadcastChanges()`；`transferStackInSlot` → `quickMoveStack`。
 *  - `ICrafting#sendProgressBarUpdate` 已删除，改用 [[net.minecraft.world.inventory.DataSlot]]：
 *    服务端 [[sendProgressBarUpdate]] 写值、客户端在 `setData(id, value)` 里读到（见 `container.Robot`）。
 *  - 自定义同步数据（原来的 `ICrafting#sendProgressBarUpdate(container, id, value)` 塞不下的部分）
 *    仍然通过 [[SynchronizedData]] 收集增量，但**投递方式**依赖尚未移植的 `server.PacketSender`，
 *    见 [[Player.customDataSync]]。
 *  - `@SideOnly` 全部删除（NeoForge 的 `RuntimeDistCleaner` 会直接抛异常）。
 *
 * ==与子类的约定==
 *  - `otherInventory` 一律是宿主物品栏（方块实体 / 机器人 / 平板 / 数据库…）；
 *  - 玩家物品栏用 [[addPlayerInventorySlots]] 添加，语义与原版一致（跳过快捷栏后再单独补快捷栏）；
 *  - 组件槽位用 [[ComponentSlot]] / [[StaticComponentSlot]] / [[DynamicComponentSlot]]。
 */
abstract class Player(windowId: Int,
                     menuType: MenuType[_],
                     val playerInventory: Inventory,
                     val otherInventory: IItemHandler)
  extends AbstractContainerMenu(menuType, windowId) {

  /** 玩家物品栏横向显示的槽位数。 */
  protected val playerInventorySizeX = math.min(9, Inventory.getSelectionSize)

  /** 玩家物品栏纵向显示的槽位数；减去 4 是原版的盔甲槽位。 */
  protected val playerInventorySizeY = math.min(4, (playerInventory.getContainerSize - 4) / playerInventorySizeX)

  /** 槽位的渲染尺寸（宽 = 高）。 */
  protected val slotSize = 18

  // ----------------------------------------------------------------------- //
  // 槽位访问
  // ----------------------------------------------------------------------- //

  /**
   * 已添加的槽位（1.21.1 的 `slots` 是 Java 的 `NonNullList`）。
   *
   * 转成 Scala 的 `IndexedSeq` 是为了让子类继续写 `inventorySlots(i)` /
   * `inventorySlots.indices` / `inventorySlots.size`，与 1.7.10 的写法保持一致。
   */
  protected def inventorySlots: IndexedSeq[Slot] = slots.asScala.toIndexedSeq

  /**
   * 取槽位背后的物品栏对象。
   *
   * 1.7.10 的 `Slot#inventory`（`IInventory`）在 1.21.1 里对应
   * `SlotItemHandler#getItemHandler`；原版 `Slot` 仍然有 `container` 字段
   * （`net.minecraft.world.Container`，玩家物品栏用它），因此这里分两种情况取。
   */
  protected def slotInventory(slot: Slot): AnyRef = slot match {
    case handlerSlot: SlotItemHandler => handlerSlot.getItemHandler
    case vanillaSlot => vanillaSlot.container
  }

  /** 槽位是否属于宿主物品栏（`otherInventory`）；原写法是 `slot.inventory == otherInventory`。 */
  protected def isOtherInventorySlot(slot: Slot): Boolean =
    slot != null && (slotInventory(slot) eq otherInventory)

  /** 1.21.1 的 `addSlot` 是 protected，这里包一层，顺带兼容 1.7.10 的方法名。 */
  protected def addSlotToContainer(slot: Slot): Slot = addSlot(slot)

  // ----------------------------------------------------------------------- //
  // 玩家能否继续操作
  // ----------------------------------------------------------------------- //

  /**
   * 玩家是否还能操作宿主物品栏（原 `IInventory#isUseableByPlayer`）。
   *
   * `IItemHandler` 没有「距离」的概念，但 OC 的方块实体统一混入了
   * [[li.cil.oc.common.tileentity.traits.Inventory]]（它保留了同名的距离判断），
   * 因此这里做一次类型匹配；非方块实体宿主（数据库 / 平板等）由子类覆写 [[stillValid]]。
   */
  protected def isUseableByPlayer(player: MCPlayer): Boolean = otherInventory match {
    case inventory: common.tileentity.traits.Inventory => inventory.isUseableByPlayer(player)
    case _ => true
  }

  override def stillValid(player: MCPlayer): Boolean = isUseableByPlayer(player)

  // ----------------------------------------------------------------------- //
  // 点击 / 转移
  // ----------------------------------------------------------------------- //

  /**
   * 原 `Container#slotClick`。
   *
   * 1.21.1 的 `clicked` 返回 `void`（结果放在 `getCarried()` 里），因此这里只保留
   * 原实现真正重要的副作用：**每次点击后强制广播一次变更** ——
   * 因为某些堆叠只要被插进特定容器就会改变「身份」（例如被分配地址），
   * 不能等到原版自己的节流窗口。
   */
  override def clicked(slot: Int, mouseClick: Int, clickType: ClickType, player: MCPlayer): Unit = {
    super.clicked(slot, mouseClick, clickType, player)
    if (SideTracker.isServer) {
      broadcastChanges()
    }
  }

  /** 原 `Container#transferStackInSlot`（Shift + 点击）。 */
  override def quickMoveStack(player: MCPlayer, index: Int): ItemStack = {
    val slot = if (index >= 0 && index < slots.size) slots.get(index) else null
    if (slot != null && slot.hasItem) {
      tryTransferStackInSlot(slot, isOtherInventorySlot(slot))
      if (SideTracker.isServer) {
        broadcastChanges()
      }
    }
    ItemStack.EMPTY
  }

  /** 返回 true 表示「已经搬完 / 没得搬」。 */
  protected def tryMoveAllSlotToSlot(from: Slot, to: Slot): Boolean = {
    if (to == null)
      return false // nowhere to move it

    if (from == null ||
      !from.hasItem ||
      from.getItem.isEmpty)
      return true // all moved because nothing to move

    if (slotInventory(to) eq slotInventory(from))
      return false // not intended for moving in the same inventory

    // for ghost slots we don't care about stack size
    val fromStack = from.getItem
    val toStack = if (to.hasItem) to.getItem else null
    val toStackSize = if (toStack != null) toStack.getCount else 0

    val maxStackSize = math.min(fromStack.getMaxStackSize, to.getMaxStackSize)
    val itemsMoved = math.min(maxStackSize - toStackSize, fromStack.getCount)

    if (toStack != null) {
      if (toStackSize < maxStackSize &&
        ItemStack.isSameItem(fromStack, toStack) &&
        ItemStack.isSameItemSameComponents(fromStack, toStack) &&
        itemsMoved > 0) {
        // 1.7.10 的 `toStack.stackSize += ...`：直接改数量，刻意**不**触发
        // onItemRemoved / onItemAdded（否则组件会被反复卸载 / 安装）。
        // OC 的物品栏后端保存的就是 ItemStack 实例本身，所以这种就地修改依然有效。
        toStack.grow(from.remove(itemsMoved).getCount)
      } else return false
    } else if (to.mayPlace(fromStack)) {
      to.set(from.remove(itemsMoved))
      if (maxStackSize == 0) {
        // Special case: we have an inventory with "phantom/ghost stacks", i.e.
        // zero size stacks, usually used for configuring machinery. In that
        // case we stop early if whatever we're shift clicking is already in a
        // slot of the target inventory. This workaround can be problematic if
        // an inventory has both real and phantom slots, but we don't have
        // something like that, yet, so hey.
        return true
      }
    } else return false

    to.setChanged()
    from.setChanged()
    false
  }

  protected def fillOrder(backFill: Boolean): Seq[Int] = {
    val indices = inventorySlots.indices
    (if (backFill) indices.reverse else indices).sortBy(i => inventorySlots(i) match {
      case s: Slot if s.hasItem => -1
      case s: ComponentSlot => s.tier
      case _ => 99
    })
  }

  protected def tryTransferStackInSlot(from: Slot, intoPlayerInventory: Boolean): Unit = {
    for (i <- fillOrder(intoPlayerInventory)) {
      if (tryMoveAllSlotToSlot(from, inventorySlots(i))) return
    }
  }

  // ----------------------------------------------------------------------- //
  // 添加槽位
  // ----------------------------------------------------------------------- //

  def addSlotToContainer(x: Int, y: Int, slot: String = common.Slot.Any, tier: Int = common.Tier.Any): Unit = {
    addSlot(new StaticComponentSlot(this, otherInventory, slots.size, x, y, slot, tier))
  }

  def addSlotToContainer(x: Int, y: Int, info: Array[Array[InventorySlot]], containerTierGetter: () => Int): Unit = {
    addSlot(new DynamicComponentSlot(this, otherInventory, slots.size, x, y,
      slot => info(slot.containerTierGetter())(slot.getSlotIndex), containerTierGetter))
  }

  def addSlotToContainer(x: Int, y: Int, info: DynamicComponentSlot => InventorySlot): Unit = {
    addSlot(new DynamicComponentSlot(this, otherInventory, slots.size, x, y, info, () => Tier.One))
  }

  /** Render player inventory at the specified coordinates. */
  protected def addPlayerInventorySlots(left: Int, top: Int): Unit = {
    // Show the inventory proper. Start at plus one to skip hot bar.
    for (slotY <- 1 until playerInventorySizeY) {
      for (slotX <- 0 until playerInventorySizeX) {
        val index = slotX + slotY * playerInventorySizeX
        val x = left + slotX * slotSize
        // Compensate for hot bar offset.
        val y = top + (slotY - 1) * slotSize
        addSlot(new Slot(playerInventory, index, x, y))
      }
    }

    // Show the quick slot bar below the internal inventory.
    val quickBarSpacing = 4
    for (index <- 0 until playerInventorySizeX) {
      val x = left + index * slotSize
      val y = top + slotSize * (playerInventorySizeY - 1) + quickBarSpacing
      addSlot(new Slot(playerInventory, index, x, y))
    }
  }

  // ----------------------------------------------------------------------- //
  // 进度条数据（原 `ICrafting#sendProgressBarUpdate`）
  // ----------------------------------------------------------------------- //

  /** 本容器登记的进度条数据槽，下标即原来 `sendProgressBarUpdate` 的 `id`。 */
  private val progressBarSlots = mutable.ArrayBuffer.empty[DataSlot]

  /**
   * 登记一个进度条数据槽，返回下标（等价于 1.7.10 里手写的 `id` 常量）。
   *
   * 服务端在 `broadcastChanges()` 里自动把变化过的 `DataSlot` 发给客户端，
   * 客户端则回调 `setData(id, value)`。
   */
  protected def addProgressBarSlot(): Int = {
    val dataSlot = DataSlot.standalone()
    progressBarSlots += dataSlot
    addDataSlot(dataSlot)
    progressBarSlots.size - 1
  }

  /** 原 `Container#sendProgressBarUpdate(id, value)`。 */
  protected def sendProgressBarUpdate(id: Int, value: Int): Unit = {
    if (id >= 0 && id < progressBarSlots.size) {
      progressBarSlots(id).set(value)
    }
  }

  // ----------------------------------------------------------------------- //
  // 自定义数据同步
  // ----------------------------------------------------------------------- //

  /** 监听本容器的对象（服务端就是 [[net.minecraft.server.level.ServerPlayer]]）。 */
  private val trackedListeners = mutable.ArrayBuffer.empty[ContainerListener]

  override def addSlotListener(listener: ContainerListener): Unit = {
    if (!trackedListeners.contains(listener)) {
      trackedListeners += listener
    }
    super.addSlotListener(listener)
  }

  override def removeSlotListener(listener: ContainerListener): Unit = {
    trackedListeners -= listener
    super.removeSlotListener(listener)
  }

  override def broadcastChanges(): Unit = {
    super.broadcastChanges()
    if (SideTracker.isServer) {
      val nbt = new CompoundTag()
      detectCustomDataChanges(nbt)
      for (listener <- trackedListeners) listener match {
        case _: FakePlayer => // Nope
        case player: ServerPlayer => Player.customDataSync(this, nbt, player)
        case _ =>
      }
    }
  }

  // Used for custom value synchronization, because shorts simply don't cut it most of the time.
  protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    val delta = synchronizedData.getDelta
    if (delta != null && !delta.isEmpty) {
      nbt.put("delta", delta)
    }
  }

  /** 客户端收到自定义数据后写回 [[synchronizedData]]（原 `updateCustomData`）。 */
  def updateCustomData(nbt: CompoundTag): Unit = {
    if (nbt.contains("delta")) {
      val delta = nbt.getCompound("delta")
      for (key <- delta.getAllKeys.asScala) {
        val value = delta.get(key)
        if (value != null) {
          synchronizedData.put(key, value)
        }
      }
    }
  }

  /**
   * 带增量的 `CompoundTag`（原实现直接继承 `NBTTagCompound` 并覆写 `setXxx`）。
   *
   * 1.7.10 的 `setTag/setInteger/setDouble/...` 在 1.21.1 改名为
   * `put/putInt/putDouble/...`，`hasNoTags` 改为 `isEmpty`，`func_150296_c()` 改为 `getAllKeys`，
   * 其余语义（写入时顺手记录「值变了」的键到 delta）保持不变。
   */
  protected class SynchronizedData extends CompoundTag {
    private var delta = new CompoundTag()

    def getDelta: CompoundTag = this.synchronized {
      if (delta.isEmpty) null
      else {
        val result = delta
        delta = new CompoundTag()
        result
      }
    }

    override def put(key: String, value: Tag): Tag = this.synchronized {
      if (!value.equals(get(key))) delta.put(key, value)
      super.put(key, value)
    }

    override def putByte(key: String, value: Byte): Unit = this.synchronized {
      if (value != getByte(key)) delta.putByte(key, value)
      super.putByte(key, value)
    }

    override def putShort(key: String, value: Short): Unit = this.synchronized {
      if (value != getShort(key)) delta.putShort(key, value)
      super.putShort(key, value)
    }

    override def putInt(key: String, value: Int): Unit = this.synchronized {
      if (value != getInt(key)) delta.putInt(key, value)
      super.putInt(key, value)
    }

    override def putLong(key: String, value: Long): Unit = this.synchronized {
      if (value != getLong(key)) delta.putLong(key, value)
      super.putLong(key, value)
    }

    override def putFloat(key: String, value: Float): Unit = this.synchronized {
      if (value != getFloat(key)) delta.putFloat(key, value)
      super.putFloat(key, value)
    }

    override def putDouble(key: String, value: Double): Unit = this.synchronized {
      if (value != getDouble(key)) delta.putDouble(key, value)
      super.putDouble(key, value)
    }

    override def putString(key: String, value: String): Unit = this.synchronized {
      if (value != getString(key)) delta.putString(key, value)
      super.putString(key, value)
    }

    override def putByteArray(key: String, value: Array[Byte]): Unit = this.synchronized {
      if (!java.util.Arrays.equals(value, getByteArray(key))) delta.putByteArray(key, value)
      super.putByteArray(key, value)
    }

    override def putIntArray(key: String, value: Array[Int]): Unit = this.synchronized {
      if (!java.util.Arrays.equals(value, getIntArray(key))) delta.putIntArray(key, value)
      super.putIntArray(key, value)
    }

    override def putBoolean(key: String, value: Boolean): Unit = this.synchronized {
      if (value != getBoolean(key)) delta.putBoolean(key, value)
      super.putBoolean(key, value)
    }
  }

  protected val synchronizedData = new SynchronizedData()
}

object Player {
  /**
   * 自定义同步数据的投递回调：`(容器, 增量 NBT, 目标玩家) => Unit`。
   *
   * 1.7.10 里是 `ServerPacketSender.sendContainerUpdate(this, nbt, player)`，
   * 而 `li.cil.oc.server.PacketSender` 属于尚未移植的 `li.cil.oc.server` 包
   * （当前编译集里只有 `server/fs` 包与 `server/component/FileSystem.scala`），
   * 因此容器层不能直接引用它。
   *
   * TODO(server.PacketSender): 服务端网络层移植后，在初始化处赋值：
   * {{{
   *   Player.customDataSync = (container, nbt, player) =>
   *     li.cil.oc.server.PacketSender.sendContainerUpdate(container, nbt, player)
   * }}}
   * 默认实现是空操作 —— 也就是说**目前自定义数据不会发给客户端**，
   * 依赖它的只是 GUI 显示（进度、机架节点映射、服务器运行状态等），不影响服务端逻辑。
   */
  var customDataSync: (Player, CompoundTag, ServerPlayer) => Unit = (_, _, _) => ()
}
