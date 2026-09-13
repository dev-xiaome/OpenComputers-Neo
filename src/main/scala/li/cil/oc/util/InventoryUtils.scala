package li.cil.oc.util

import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler

/**
 * 物品栏（`IItemHandler`）相关工具。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `net.neoforged.neoforge.items.IItemHandler`
 *  - `getSizeInventory` → `getSlots`，`getStackInSlot` 同名
 *  - `isItemValidForSlot` → `isItemValid`
 *  - `setInventorySlotContents` / `decrStackSize` → `insertItem` / `extractItem`
 *    （新 API 是“插入/抽取”语义：插入返回剩余物，抽取返回实际抽出的物品）
 *  - `ISidedInventory` 的“按面可访问槽位”概念已移除，改用 `IItemHandler` 的槽位校验
 *  - 物品栏不再逐个方块实体实现，改为 NeoForge 能力（`Capabilities.ItemHandler.BLOCK` / `.ENTITY`）
 *  - `ItemStack.areItemStackTagsEqual` → `ItemStack.isSameItemSameComponents`
 *  - `stackSize` → `getCount()` / `setCount()`，空栈判断用 `isEmpty`
 *  - 双箱子（`BlockChest` + `TileEntityChest`）的特殊处理已被 Minecraft 移除，逻辑一并删除
 */
object InventoryUtils {
  /**
   * Check if two item stacks are of equal type, ignoring the stack size.
   * <br>
   * Optionally check for equality in NBT data.
   */
  def haveSameItemType(stackA: ItemStack, stackB: ItemStack, checkNBT: Boolean = false) =
    stackA != null && stackB != null && !stackA.isEmpty && !stackB.isEmpty &&
      stackA.is(stackB.getItem) &&
      (!checkNBT || ItemStack.isSameItemSameComponents(stackA, stackB))

  /**
   * Retrieves an actual inventory implementation for a specified world coordinate,
   * complete with a reference to the source of said implementation.
   * <br>
   * This also checks for mine carts with chests.
   */
  def inventorySourceAt(position: BlockPosition): Option[InventorySource] = position.world match {
    case Some(world) if world.isLoaded(position.toChunkCoordinates) =>
      val pos = position.toChunkCoordinates
      val blockInventory = Option(world.getBlockEntity(pos)).
        flatMap(_.getCapability(Capabilities.ItemHandler.BLOCK, null))
      blockInventory match {
        case Some(inventory) => Some(BlockInventorySource(position, inventory))
        case _ =>
          val entity = world.getEntitiesOfClass(classOf[AbstractMinecartContainer], position.bounds).
            find(!_.isRemoved)
          entity match {
            case Some(cart) => Option(cart.getCapability(Capabilities.ItemHandler.ENTITY)).
              map(inventory => EntityInventorySource(cart, inventory))
            case _ => None
          }
      }
    case _ => None
  }

  /**
   * Retrieves an actual inventory implementation for a specified world coordinate.
   * <br>
   * This also checks for mine carts with chests.
   */
  def inventoryAt(position: BlockPosition): Option[IItemHandler] = inventorySourceAt(position).
    map(a => a.inventory)

  /**
   * Inserts a stack into an inventory.
   * <br>
   * Only tries to insert into the specified slot. This <em>cannot</em> be
   * used to empty a slot. It can only insert stacks into empty slots and
   * merge additional items into an existing stack in the slot.
   * <br>
   * The passed stack's size will be adjusted to reflect the number of items
   * inserted into the inventory, i.e. if 10 more items could fit into the
   * slot, the stack's size will be 10 smaller than before the call.
   * <br>
   * This will return <tt>true</tt> if <em>at least</em> one item could be
   * inserted into the slot. It will return <tt>false</tt> if the passed
   * stack did not change.
   * <br>
   * The number of items inserted can be limited, to avoid unnecessary
   * changes to the inventory the stack may come from, for example.
   */
  def insertIntoInventorySlot(stack: ItemStack, inventory: IItemHandler, side: Option[Direction], slot: Int, limit: Int = 64, simulate: Boolean = false): Boolean =
    insertIntoInventorySlotCount(stack, inventory, side, slot, limit, simulate) > 0

  /**
   * 与 [[insertIntoInventorySlot]] 相同，但返回成功插入的物品数量。
   * <tt>simulate</tt> 为真时不修改任何状态，只返回“可以插入”的数量。
   */
  private def insertIntoInventorySlotCount(stack: ItemStack, inventory: IItemHandler, side: Option[Direction], slot: Int, limit: Int, simulate: Boolean): Int = {
    if (stack == null || stack.isEmpty || limit <= 0 || slot < 0 || slot >= inventory.getSlots) return 0
    // `side` 仅用于兼容旧签名，1.21.1 的面过滤由能力提供方（BlockCapability 的 Direction 上下文）完成。
    val maxStackSize = math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize)
    if (maxStackSize <= 0) return 0

    val existing = inventory.getStackInSlot(slot)
    if (existing != null && !existing.isEmpty) {
      // 合并进已有堆叠。
      val canMerge = existing.is(stack.getItem) &&
        ItemStack.isSameItemSameComponents(existing, stack) &&
        !existing.isDamaged &&
        existing.getCount < maxStackSize
      if (!canMerge) return 0

      val amount = math.min(limit, math.min(stack.getCount, maxStackSize - existing.getCount))
      if (amount <= 0) return 0
      if (simulate) return amount

      val inserted = insertIntoSlot(inventory, slot, stack, amount)
      stack.shrink(inserted)
      inserted
    }
    else {
      // 放进空槽位。
      if (!inventory.isItemValid(slot, stack)) return 0

      val amount = math.min(limit, math.min(stack.getCount, maxStackSize))
      if (amount <= 0) return 0
      if (simulate) return amount

      val inserted = insertIntoSlot(inventory, slot, stack, amount)
      stack.shrink(inserted)
      inserted
    }
  }

  /** 把 `stack` 中的至多 `amount` 个物品插入槽位，返回实际被接受的个数。 */
  private def insertIntoSlot(inventory: IItemHandler, slot: Int, stack: ItemStack, amount: Int): Int = {
    val remainder = inventory.insertItem(slot, stack.copyWithCount(amount), false)
    amount - (if (remainder == null) 0 else remainder.getCount)
  }

  /**
   * Extracts a stack from an inventory.
   * <br>
   * Only tries to extract from the specified slot. This <em>can</em> be used
   * to empty a slot. It will extract items using the specified consumer method
   * which is called with the extracted stack before the stack in the inventory
   * that we extract from is cleared from.
   * <br>
   * The consumer is the only way to retrieve the actually extracted stack.
   * <br>
   * This will return the <tt>number</tt> of items extracted. It will return
   * <tt>zero</tt> if the stack in the slot did not change.
   */
  def extractFromInventorySlot(consumer: ItemStack => Unit, inventory: IItemHandler, side: Direction, slot: Int, limit: Int = 64): Int =
    extractFromInventorySlot(consumer, inventory, side, slot, limit, simulate = false)

  private def extractFromInventorySlot(consumer: ItemStack => Unit, inventory: IItemHandler, side: Direction, slot: Int, limit: Int, simulate: Boolean): Int = {
    if (limit <= 0 || slot < 0 || slot >= inventory.getSlots) return 0

    val inSlot = inventory.getStackInSlot(slot)
    if (inSlot == null || inSlot.isEmpty) return 0

    val amount = math.min(limit, inSlot.getCount)
    val extracted = inventory.extractItem(slot, amount, simulate)
    if (extracted == null || extracted.isEmpty) return 0

    val before = extracted.getCount
    consumer(extracted)
    if (simulate) {
      // 模拟模式不修改物品栏，回报“可以抽出”的数量。
      before
    }
    else {
      val taken = before - extracted.getCount
      if (extracted.getCount > 0) {
        // 消费方没有全部拿走，把剩余塞回原槽位。
        inventory.insertItem(slot, extracted, false)
      }
      taken
    }
  }

  /**
   * Inserts a stack into an inventory.
   * <br>
   * This will try to fit the stack in any and as many as necessary slots in
   * the inventory. It will first try to merge the stack in stacks already
   * present in the inventory. After that it will try to fit the stack into
   * empty slots in the inventory.
   * <br>
   * This returns <tt>true</tt> if at least one item was inserted. The passed
   * item stack will be adjusted to reflect the number items inserted, by
   * having its size decremented accordingly.
   */
  def insertIntoInventory(stack: ItemStack, inventory: IItemHandler, side: Option[Direction] = None, limit: Int = 64, simulate: Boolean = false, slots: Option[Iterable[Int]] = None): Boolean =
    (stack != null && !stack.isEmpty && limit > 0) && {
      var success = false
      var remaining = limit
      // 1.21.1 的 IItemHandler 不再按面暴露槽位子集，默认遍历全部槽位。
      val range = slots.getOrElse(0 until inventory.getSlots)

      if (range.nonEmpty) {
        // This is a special case for inserting with an explicit ordering,
        // such as when inserting into robots, where the range starts at the
        // selected slot. In that case we want to prefer inserting into that
        // slot, if at all possible, over merging.
        if (slots.isDefined) {
          val inserted = insertIntoInventorySlotCount(stack, inventory, side, range.head, remaining, simulate)
          if (inserted > 0) {
            remaining -= inserted
            success = true
          }
        }

        val shouldTryMerge = !stack.isDamageableItem && stack.getMaxStackSize > 1
        if (shouldTryMerge) {
          for (slot <- range if remaining > 0) {
            val inserted = insertIntoInventorySlotCount(stack, inventory, side, slot, remaining, simulate)
            if (inserted > 0) {
              remaining -= inserted
              success = true
            }
          }
        }

        for (slot <- range if remaining > 0) {
          val inserted = insertIntoInventorySlotCount(stack, inventory, side, slot, remaining, simulate)
          if (inserted > 0) {
            remaining -= inserted
            success = true
          }
        }
      }

      success
    }

  /**
   * Extracts a slot from an inventory.
   * <br>
   * This will try to extract a stack from any inventory slot. It will iterate
   * all slots until an item can be extracted from a slot.
   * <br>
   * This returns the <tt>number</tt> of items extracted. It will return
   * <tt>zero</tt> if nothing could be extracted.
   */
  def extractAnyFromInventory(consumer: ItemStack => Unit, inventory: IItemHandler, side: Direction, limit: Int = 64): Int = {
    for (slot <- 0 until inventory.getSlots) {
      val extracted = extractFromInventorySlot(consumer, inventory, side, slot, limit, simulate = false)
      if (extracted > 0) return extracted
    }
    0
  }

  /**
   * Extracts an item stack from an inventory.
   * <br>
   * This will try to remove items of the same type as the specified item stack
   * up to the number of the stack's size for all slots in the specified inventory.
   * If exact is true, the items collected will also match components (等价于旧版 metadata)。
   * <br>
   * This returns the number of items actually extracted.
   */
  def extractFromInventory(stack: ItemStack, inventory: IItemHandler, side: Direction, simulate: Boolean = false, exact: Boolean = true): Int = {
    var extracted = 0
    for (slot <- 0 until inventory.getSlots if stack.getCount > 0) {
      extracted += extractFromInventorySlot(stackInInv => {
        if (stackInInv != null && !stackInInv.isEmpty && stackInInv.is(stack.getItem) &&
          (!exact || haveSameItemType(stack, stackInInv, checkNBT = true))) {
          val transferred = math.min(stackInInv.getCount, stack.getCount)
          // `stack` 仅作为“还需要抽多少”的计数器使用。
          stack.shrink(transferred)
        }
      }, inventory, side, slot, stack.getCount, simulate)
    }
    extracted
  }

  /**
   * Utility method for calling <tt>insertIntoInventory</tt> on an inventory
   * in the world.
   */
  def insertIntoInventoryAt(stack: ItemStack, position: BlockPosition, side: Option[Direction] = None, limit: Int = 64, simulate: Boolean = false): Boolean =
    inventoryAt(position).exists(insertIntoInventory(stack, _, side, limit, simulate))

  type Extractor = () => Int

  /**
   * Utility method for calling <tt>extractFromInventory</tt> on an inventory
   * in the world.
   */
  def getExtractorFromInventoryAt(consumer: ItemStack => Unit, position: BlockPosition, side: Direction, limit: Int = 64): Extractor =
    inventoryAt(position) match {
      case Some(inventory) => () => extractAnyFromInventory(consumer, inventory, side, limit)
      case _ => null
    }

  /**
   * Transfers some items between two inventories.
   * <br>
   * This returns the number of items transferred.
   */
  def transferBetweenInventories(source: IItemHandler, sourceSide: Direction, sink: IItemHandler, sinkSide: Option[Direction], limit: Int = 64): Int =
    extractAnyFromInventory(
      insertIntoInventory(_, sink, sinkSide, limit), source, sourceSide, limit)

  /**
   * Like <tt>transferBetweenInventories</tt> but moving between specific slots.
   */
  def transferBetweenInventoriesSlots(source: IItemHandler, sourceSide: Direction, sourceSlot: Int, sink: IItemHandler, sinkSide: Option[Direction], sinkSlot: Option[Int], limit: Int = 64): Int =
    sinkSlot match {
      case Some(explicitSinkSlot) =>
        extractFromInventorySlot(
          insertIntoInventorySlot(_, sink, sinkSide, explicitSinkSlot, limit), source, sourceSide, sourceSlot, limit)
      case _ =>
        extractFromInventorySlot(
          insertIntoInventory(_, sink, sinkSide, limit), source, sourceSide, sourceSlot, limit)
    }

  /**
   * Utility method for calling <tt>transferBetweenInventories</tt> on inventories
   * in the world.
   */
  def getTransferBetweenInventoriesAt(source: BlockPosition, sourceSide: Direction, sink: BlockPosition, sinkSide: Option[Direction], limit: Int = 64): Extractor =
    inventoryAt(source) match {
      case Some(sourceInventory) =>
        inventoryAt(sink) match {
          case Some(sinkInventory) => () => transferBetweenInventories(sourceInventory, sourceSide, sinkInventory, sinkSide, limit)
          case _ => null
        }
      case _ => null
    }

  /**
   * Utility method for calling <tt>transferBetweenInventoriesSlots</tt> on inventories
   * in the world.
   */
  def getTransferBetweenInventoriesSlotsAt(sourcePos: BlockPosition, sourceSide: Direction, sourceSlot: Int, sinkPos: BlockPosition, sinkSide: Option[Direction], sinkSlot: Option[Int], limit: Int = 64): Extractor =
    inventoryAt(sourcePos) match {
      case Some(sourceInventory) =>
        inventoryAt(sinkPos) match {
          case Some(sinkInventory) => () => transferBetweenInventoriesSlots(sourceInventory, sourceSide, sourceSlot, sinkInventory, sinkSide, sinkSlot, limit)
          case _ => null
        }
      case _ => null
    }

  /**
   * Utility method for dropping contents from a single inventory slot into
   * the world.
   */
  def dropSlot(position: BlockPosition, inventory: IItemHandler, slot: Int, count: Int, direction: Option[Direction] = None): Boolean = {
    val extracted = inventory.extractItem(slot, count, false)
    if (extracted != null && !extracted.isEmpty) {
      spawnStackInWorld(position, extracted, direction)
      true
    }
    else false
  }

  /**
   * Utility method for dumping all inventory contents into the world.
   */
  def dropAllSlots(position: BlockPosition, inventory: IItemHandler): Unit = {
    // 从后往前抽取，避免抽取过程中槽位前移造成的遗漏。
    for (slot <- (0 until inventory.getSlots).reverse) {
      val stack = inventory.getStackInSlot(slot)
      if (stack != null && !stack.isEmpty) {
        val extracted = inventory.extractItem(slot, stack.getCount, false)
        if (extracted != null && !extracted.isEmpty) {
          spawnStackInWorld(position, extracted)
        }
      }
    }
  }

  /**
   * Try inserting an item stack into a player inventory. If that fails, drop it into the world.
   */
  def addToPlayerInventory(stack: ItemStack, player: Player, spawnInWorld: Boolean = true): Unit = {
    if (stack != null && !stack.isEmpty) {
      // 1.21.1 的 `Inventory#add` 会把放不下的部分留在栈内。
      if (player.getInventory.add(stack)) {
        player.getInventory.setChanged()
        if (player.containerMenu != null) {
          player.containerMenu.broadcastChanges()
        }
      }
      if (!stack.isEmpty && spawnInWorld) {
        player.drop(stack, false)
      }
    }
  }

  /**
   * Utility method for spawning an item stack in the world.
   */
  def spawnStackInWorld(position: BlockPosition, stack: ItemStack, direction: Option[Direction] = None, validator: Option[ItemEntity => Boolean] = None): ItemEntity = position.world match {
    case Some(world) if stack != null && !stack.isEmpty =>
      val rng = world.getRandom
      val (ox, oy, oz) = direction.fold((0, 0, 0))(d => (d.getStepX, d.getStepY, d.getStepZ))
      val (tx, ty, tz) = (
        0.1 * (rng.nextDouble - 0.5) + ox * 0.65,
        0.1 * (rng.nextDouble - 0.5) + oy * 0.75 + (ox + oz) * 0.25,
        0.1 * (rng.nextDouble - 0.5) + oz * 0.65)
      val dropPos = position.offset(0.5 + tx, 0.5 + ty, 0.5 + tz)
      val entity = new ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, stack.copy())
      entity.setDeltaMovement(
        0.0125 * (rng.nextDouble - 0.5) + ox * 0.03,
        0.0125 * (rng.nextDouble - 0.5) + oy * 0.08 + (ox + oz) * 0.03,
        0.0125 * (rng.nextDouble - 0.5) + oz * 0.03)
      entity.setPickUpDelay(15)
      if (validator.fold(true)(_(entity))) {
        world.addFreshEntity(entity)
        entity
      }
      else null
    case _ => null
  }
}

sealed trait InventorySource {
  def inventory: IItemHandler
}
final case class BlockInventorySource(position: BlockPosition, inventory: IItemHandler) extends InventorySource
final case class EntityInventorySource(entity: Entity, inventory: IItemHandler) extends InventorySource
