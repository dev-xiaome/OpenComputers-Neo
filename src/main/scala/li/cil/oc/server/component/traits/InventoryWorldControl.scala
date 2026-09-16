package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.Direction
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.{BlockItem, ItemStack}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.item.ItemTossEvent

/**
 * 设备与相邻方块/掉落物之间的物品交互（对应 1.7.10 的 `traits.InventoryWorldControl`）。
 *
 * 1.21.1 迁移要点：
 *  - `ItemBlock` → `BlockItem`，`item.field_150939_a` → `BlockItem#getBlock`
 *  - 方块 metadata 已移除，[[compare]] 的非 fuzzy 分支退化为比较完整方块状态
 *  - `MinecraftForge` → `NeoForge`，`ItemTossEvent` 改成 NeoForge 的包名；
 *    新事件没有 `Event.Result`，只用 `isCanceled`
 *  - `EntityItem` → `ItemEntity`；`isDead` → `isRemoved`，`getEntityItem` → `getItem`，
 *    `onCollideWithPlayer` → `playerTouch`
 *  - `IInventory#setInventorySlotContents` / `decrStackSize` / `markDirty` →
 *    [[InventorySlots]] 与 [[InventoryAware]] 的 `setStackInSlot` / `markInventoryDirty`
 */
trait InventoryWorldControl extends InventoryAware with WorldAware with SideRestricted {
  @Callback(doc = "function(side:number[, fuzzy:boolean=false]):boolean -- Compare the block on the specified side with the one in the selected slot. Returns true if equal.")
  def compare(context: Context, args: Arguments): Array[AnyRef] = {
    val side = checkSideForAction(args, 0)
    stackInSlot(selectedSlot) match {
      case Some(stack) => stack.getItem match {
        case item: BlockItem =>
          val blockPos = position.offset(side)
          val blockState = world.getBlockState(blockPos.toChunkCoordinates)
          val idMatches = item.getBlock == blockState.getBlock
          // 1.21.1 没有方块 metadata：非 fuzzy 分支退化为比较完整方块状态，
          // 语义与旧版一致（比默认状态多出额外属性就算不同）。
          val subTypeMatches = args.optBoolean(1, false) || blockState == item.getBlock.defaultBlockState
          return result(idMatches && subTypeMatches)
        case _ =>
      }
      case _ =>
    }
    result(false)
  }

  @Callback(doc = "function(side:number[, count:number=64]):boolean -- Drops items from the selected slot towards the specified side.")
  def drop(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val count = args.optItemCount(1)
    val stack = inventory.getStackInSlot(selectedSlot)
    if (stack != null && !stack.isEmpty) {
      val blockPos = position.offset(facing)
      InventoryUtils.inventoryAt(blockPos) match {
        // TODO(server): 1.21.1 的 IItemHandler 没有 `isUseableByPlayer` 的等价查询
        //（旧版用它判断玩家能否使用该物品栏，例如原版工作台/铁砧），这里退化为只检查交互权限。
        case Some(inv) if mayInteract(blockPos, facing.getOpposite) =>
          if (!InventoryUtils.insertIntoInventory(stack, inv, Option(facing.getOpposite), count)) {
            // Cannot drop into that inventory.
            return result(false, "inventory full")
          }
          else if (stack.isEmpty) {
            // Dropped whole stack.
            setStackInSlot(selectedSlot, ItemStack.EMPTY)
          }
          else {
            // Dropped partial stack. 1.21.1 的物品栏不保证返回可写的槽位引用，显式写回。
            setStackInSlot(selectedSlot, stack)
            markInventoryDirty()
          }
        case _ =>
          // No inventory to drop into, drop into the world.
          val dropped = InventorySlots.decrStackSize(inventory, selectedSlot, count)
          val validator = (item: ItemEntity) => {
            val event = new ItemTossEvent(item, fakePlayer)
            !NeoForge.EVENT_BUS.post(event).isCanceled
          }
          if (dropped != null && !dropped.isEmpty) {
            if (InventoryUtils.spawnStackInWorld(position, dropped, Some(facing), Some(validator)) == null)
              fakePlayer.getInventory.add(dropped)
          }
      }

      context.pause(Settings.get.dropDelay)

      result(true)
    }
    else result(false)
  }

  /**
    * @param facing items to suck from
    * @return the number of items sucked
    */
  def suckFromItems(facing: Direction): Int = {
    // TODO(server): 1.21.1 的 ItemEntity 不再暴露 `delayBeforeCanPickup` 的读数，
    // 只有 `hasPickUpDelay`，这里用它近似旧版的 "delayBeforeCanPickup <= 0" 判断。
    for (entity <- suckableItems(facing) if !entity.isRemoved && !entity.hasPickUpDelay) {
      val stack = entity.getItem
      val size = if (stack == null) 0 else stack.getCount
      onSuckCollect(entity)
      val remaining = if (stack == null) 0 else stack.getCount
      if (remaining < size)
        return size - remaining
      else if (entity.isRemoved)
        return size
    }
    0
  }

  @Callback(doc = "function(side:number[, count:number=64]):boolean -- Suck up items from the specified side.")
  def suck(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val count = args.optItemCount(1)

    val blockPos = position.offset(facing)
    var extracted: Int = InventoryUtils.inventoryAt(blockPos) match {
      case Some(inv) =>
        // 保持 1.7.10 的原有写法：这里只是表达“需要交互权限”，布尔结果并未参与分支
        //（旧版 IInventory 的 `isUseableByPlayer` 检查已无 1.21.1 等价物）。
        mayInteract(blockPos, facing.getOpposite)
        InventoryUtils.extractAnyFromInventory(InventoryUtils.insertIntoInventory(_, this.inventory, slots = Option(insertionSlots)), inv, facing.getOpposite, count)
      case _ => 0
    }
    if (extracted <= 0) {
      extracted = suckFromItems(facing)
    }
    if (extracted <= 0) {
      result(false)
    } else {
      context.pause(Settings.get.suckDelay)
      result(extracted)
    }
  }

  protected def suckableItems(side: Direction) = entitiesOnSide[ItemEntity](side)

  protected def onSuckCollect(entity: ItemEntity): Unit = entity.playerTouch(fakePlayer)
}
