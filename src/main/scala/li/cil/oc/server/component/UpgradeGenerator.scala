package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.server.component.traits.InventorySlots
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity

import scala.jdk.CollectionConverters._

/**
 * 发电机升级（燃料队列 + 供电）。
 *
 * ==1.21.1 迁移要点==
 *  - `TileEntityFurnace.isItemFuel / getItemBurnTime` 已随 `TileEntityFurnace` 移除：
 *    1.21.1 的燃料判定是 [[net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity#isFuel]]，
 *    燃烧时间走 NeoForge 的 `ItemStack#getBurnTime`（`FURNACE_FUELS` 数据表驱动）。
 *    两者底层都是 `getBurnTime(null)`，因此这里保持一致的取值方式。
 *  - `Item#getContainerItem` → `ItemStack#getCraftingRemainingItem`（合成剩余物语义，空栈而非 `null`）。
 *  - `IInventory` → `IItemHandler`：写槽位统一走 [[InventorySlots]]。
 *  - `player.inventory` → `player.getInventory`（1.21.1 的 `Inventory` 仍实现 `Container`，
 *    `add` 会把放不下的部分留在栈内）。
 */
class UpgradeGenerator(val host: EnvironmentHost with internal.Agent) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("generator", Visibility.Neighbors).
    withConnector().
    create()

  var inventory: Option[ItemStack] = None

  var remainingTicks = 0

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Generator",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Portagen 2.0 (Rev. 3)",
    DeviceAttribute.Capacity -> "1"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function([count:number]):boolean -- Tries to insert fuel from the selected slot into the generator's queue.""")
  def insert(context: Context, args: Arguments): Array[AnyRef] = {
    val count = args.optInteger(0, 64)
    val stack = host.mainInventory.getStackInSlot(host.selectedSlot)
    if (stack == null || stack.isEmpty) return result(Unit, "selected slot is empty")
    if (!AbstractFurnaceBlockEntity.isFuel(stack)) {
      return result(Unit, "selected slot does not contain fuel")
    }
    val container: ItemStack = stack.getCraftingRemainingItem
    val inQueue: ItemStack = inventory match {
      case Some(q) if q != null && !q.isEmpty =>
        // 旧实现分两步比较「物品 + 损伤值」与 NBT；1.21.1 的 components 已涵盖两者。
        if (!ItemStack.isSameItemSameComponents(q, stack)) {
          return result(Unit, "different fuel type already queued")
        }
        q
      case _ => null
    }
    val space = Option(inQueue) match {
      case Some(q) if q != null && !q.isEmpty => q.getMaxStackSize - q.getCount
      case _ => stack.getMaxStackSize
    }
    if (space == 0) {
      return result(Unit, "queue is full")
    }
    val previousSelectedFuel: ItemStack = stack.copy
    val insertLimit: Int = math.min(stack.getCount, math.min(space, count))
    val fuelToInsert: ItemStack = stack.split(insertLimit)

    // remove the fuel from the inventory
    if (stack.isEmpty) {
      InventorySlots.setStack(InventorySlots.wrap(host.mainInventory), host.selectedSlot, null)
    } else {
      InventorySlots.setStack(InventorySlots.wrap(host.mainInventory), host.selectedSlot, stack)
    }

    // add empty containers to inventory
    if (container != null && !container.isEmpty) {
      container.setCount(fuelToInsert.getCount)
      val beforeAdd = container.getCount
      host.player.getInventory.add(container)
      if (container.getCount == beforeAdd) {
        // no containers could be placed in inventory, give back the fuel
        InventorySlots.setStack(InventorySlots.wrap(host.mainInventory), host.selectedSlot, previousSelectedFuel)
        return result(false, "no space in inventory for fuel containers")
      } else if (container.getCount > 0) {
        // not all the containers could be inserted in the inventory:
        // 1.7.10 的 `EntityPlayer#entityDropItem` 等价于生成一个初始 `motionY = offsetY` 的掉落物。
        val world = host.world
        val entity = new ItemEntity(world, host.xPosition(), host.yPosition(), host.zPosition(), container.copy)
        entity.setDeltaMovement(0, -0.25, 0)
        world.addFreshEntity(entity)
      }
    }

    if (inQueue != null) {
      fuelToInsert.grow(inQueue.getCount)
    }

    inventory = Option(fuelToInsert)

    result(true, insertLimit)
  }

  @Callback(doc = """function():number -- Get the size of the item stack in the generator's queue.""")
  def count(context: Context, args: Arguments): Array[AnyRef] = {
    inventory match {
      case Some(stack) => result(stack.getCount, stack.getHoverName.getString)
      case _ => result(0)
    }
  }

  @Callback(doc = """function([count:number]):boolean -- Tries to remove items from the generator's queue.""")
  def remove(context: Context, args: Arguments): Array[AnyRef] = {
    val count = args.optInteger(0, Int.MaxValue)
    if (count <= 0) {
      return result(true) // it is allowed to remove zero
    }
    val inQueue: ItemStack = inventory match {
      case Some(q) if q != null && !q.isEmpty => q
      case _ => null
    }
    if (inQueue == null) {
      return result(false, "queue is empty")
    }
    val previousSelectedItem: ItemStack = host.mainInventory.getStackInSlot(host.selectedSlot) match {
      case s: ItemStack if s != null && !s.isEmpty => s.copy
      case _ => null
    }
    val selectedEmptyContainer: Option[ItemStack] = inQueue.getCraftingRemainingItem match {
      case requiredContainer if requiredContainer != null && !requiredContainer.isEmpty => previousSelectedItem match {
        case slotItem: ItemStack if
          slotItem != null &&
            !slotItem.isEmpty &&
            ItemStack.isSameItemSameComponents(slotItem, requiredContainer) => Option(slotItem.copy)
        case _ => return result(false, "removing this fuel requires the appropriate container in the selected slot")
      }
      case _ => None // nothing to do, nothing required
    }

    val removeLimit: Int = math.min(inQueue.getCount, selectedEmptyContainer match {
      case Some(emptyContainer) => emptyContainer.getCount
      case _ => count
    })

    // backup in case of failure
    val previousQueue = inQueue.copy
    val forUser = inQueue.split(removeLimit)
    selectedEmptyContainer match {
      case Some(emptyContainer) =>
        emptyContainer.split(removeLimit)
        if (emptyContainer.isEmpty) {
          InventorySlots.setStack(InventorySlots.wrap(host.mainInventory), host.selectedSlot, null)
        } else {
          InventorySlots.decrStackSize(host.mainInventory, host.selectedSlot, removeLimit)
        }
      case _ => // do nothing
    }
    // `Inventory#add` 会把放不进玩家物品栏的部分留在传入的栈里，
    // 因此「数量没变」才表示一个都没能放进（对应 1.7.10 返回 false 的情形）。
    val beforeAdd = forUser.getCount
    host.player.getInventory.add(forUser)
    if (forUser.getCount == beforeAdd) {
      // no inventory space available for fuel
      InventorySlots.setStack(InventorySlots.wrap(host.mainInventory), host.selectedSlot, previousSelectedItem)
      inventory = Option(previousQueue)
      result(false, "no inventory space available for fuel")
    } else {
      previousQueue.grow(forUser.getCount)
      inventory = if (previousQueue.isEmpty) None else Option(previousQueue)
      result(true, removeLimit - forUser.getCount)
    }
  }

  // ----------------------------------------------------------------------- //

  override val canUpdate = true

  override def update(): Unit = {
    super.update()
    if (remainingTicks <= 0 && inventory.isDefined) {
      val stack = inventory.get
      // 1.7.10: `TileEntityFurnace.getItemBurnTime(stack)`；
      // 1.21.1 走 NeoForge 数据表驱动的 `ItemStack#getBurnTime`（与 `isFuel` 同源）。
      remainingTicks = stack.getBurnTime(null)
      if (remainingTicks > 0) {
        updateClient()
        stack.shrink(1)
        if (stack.isEmpty) {
          // do not put container in inventory (we left the container when fuel was inserted)
          inventory = None
        }
      }
    }
    if (remainingTicks > 0) {
      remainingTicks -= 1
      if (remainingTicks == 0 && inventory.isEmpty) {
        updateClient()
      }
      node.changeBuffer(Settings.get.generatorEfficiency)
    }
  }

  private def updateClient(): Unit = host match {
    case robot: internal.Robot => robot.synchronizeSlot(robot.componentSlot(node.address))
    case _ =>
  }

  // ----------------------------------------------------------------------- //

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      inventory match {
        case Some(stack) =>
          val world = host.world
          val entity = new ItemEntity(world, host.xPosition(), host.yPosition(), host.zPosition(), stack.copy())
          entity.setDeltaMovement(0, 0.04, 0)
          entity.setPickUpDelay(5)
          world.addFreshEntity(entity)
          inventory = None
        case _ =>
      }
      remainingTicks = 0
    }
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    if (nbt.contains("inventory")) {
      // 1.21.1 的物品反序列化需要注册表访问器（旧 `ItemStack.loadItemStackFromNBT`）。
      inventory = Option(ItemStack.parseOptional(host.world.registryAccess(), nbt.getCompound("inventory"))).
        filterNot(_.isEmpty)
    }
    remainingTicks = nbt.getInt("remainingTicks")
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    inventory match {
      case Some(stack) => nbt.setNewCompoundTag("inventory", tag => stack.save(host.world.registryAccess(), tag))
      case _ =>
    }
    if (remainingTicks > 0) {
      nbt.putInt("remainingTicks", remainingTicks)
    }
  }
}
