package li.cil.oc.server.component

import java.io.ByteArrayOutputStream
import java.util

import com.google.common.hash.Hashing
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.common.item.data.StackSerializer
import li.cil.oc.server.component.traits.InventorySlots
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.NbtIo
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

import scala.jdk.CollectionConverters._

/**
 * 「数据库升级」组件（1.7.10 的 `server.component.UpgradeDatabase`）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → [[IItemHandler]]：`getSizeInventory` → `getSlots`，
 *    `setInventorySlotContents` → [[InventorySlots.setStack]]，
 *    空槽返回 `ItemStack.EMPTY` 而不是 `null`。
 *  - `NbtIo.compress(ItemStack)`（1.7.10 的 Forge 便捷方法）已不存在，
 *    改为 [[StackSerializer.toTag]] + `NbtIo.writeCompressed` 得到等价的 gzip NBT 字节。
 *  - 注意：[[DatabaseAccess.withDatabase]] 的结构类型要求 `data` 是 `IItemHandler`，
 *    这里的字段类型与之严格一致。
 */
class UpgradeDatabase(val data: IItemHandler) extends prefab.ManagedEnvironment with internal.Database with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("database").
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Object catalogue",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "iCatalogue (patent pending)",
    DeviceAttribute.Capacity -> size.toString
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  override def size = data.getSlots

  /** 空槽在 1.7.10 返回 `null`，这里把 `ItemStack.EMPTY` 归一化成 `null` 以保持旧语义。 */
  private def stackInSlot(slot: Int): ItemStack = {
    if (slot < 0 || slot >= data.getSlots) return null
    val stack = data.getStackInSlot(slot)
    if (stack == null || stack.isEmpty) null else stack
  }

  override def getStackInSlot(slot: Int) = Option(stackInSlot(slot)).map(_.copy()).orNull

  override def setStackInSlot(slot: Int, stack: ItemStack): Unit =
    InventorySlots.setStack(InventorySlots.wrap(data), slot, if (stack == null) ItemStack.EMPTY else stack)

  override def findStackWithHash(needle: String) = indexOf(needle)

  @Callback(doc = "function(slot:number):table -- Get the representation of the item stack stored in the specified slot.")
  def get(context: Context, args: Arguments): Array[AnyRef] = result(stackInSlot(args.checkSlot(data, 0)))

  @Callback(doc = "function(slot:number):string -- Computes a hash value for the item stack in the specified slot.")
  def computeHash(context: Context, args: Arguments): Array[AnyRef] = {
    val stack = stackInSlot(args.checkSlot(data, 0))
    if (stack == null) null
    else result(hashOf(stack))
  }

  @Callback(doc = "function(hash:string):number -- Get the index of an item stack with the specified hash. Returns a negative value if no such stack was found.")
  def indexOf(context: Context, args: Arguments): Array[AnyRef] = result(indexOf(args.checkString(0), 1))

  @Callback(doc = "function(slot:number):boolean -- Clears the specified slot. Returns true if there was something in the slot before.")
  def clear(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = args.checkSlot(data, 0)
    val nonEmpty = stackInSlot(slot) != null
    setStackInSlot(slot, null)
    result(nonEmpty)
  }

  @Callback(doc = "function(fromSlot:number, toSlot:number[, address:string]):boolean -- Copies an entry to another slot, optionally to another database. Returns true if something was overwritten.")
  def copy(context: Context, args: Arguments): Array[AnyRef] = {
    val fromSlot = args.checkSlot(data, 0)
    val entry = stackInSlot(fromSlot)
    def set(inventory: IItemHandler) = {
      val toSlot = args.checkSlot(inventory, 1)
      val nonEmpty = {
        if (toSlot < 0 || toSlot >= inventory.getSlots) false
        else {
          val existing = inventory.getStackInSlot(toSlot)
          existing != null && !existing.isEmpty
        }
      }
      InventorySlots.setStack(InventorySlots.wrap(inventory), toSlot,
        if (entry == null) ItemStack.EMPTY else entry.copy())
      result(nonEmpty)
    }
    if (args.count > 2) DatabaseAccess.withDatabase(node, args.checkString(2), database => set(database.data))
    else set(data)
  }

  @Callback(doc = "function(address:string):number -- Copies the data stored in this database to another database with the specified address.")
  def clone(context: Context, args: Arguments): Array[AnyRef] = {
    DatabaseAccess.withDatabase(node, args.checkString(0), database => {
      val numberToCopy = math.min(data.getSlots, database.data.getSlots)
      for (slot <- 0 until numberToCopy) {
        val stack = stackInSlot(slot)
        InventorySlots.setStack(InventorySlots.wrap(database.data), slot,
          if (stack == null) ItemStack.EMPTY else stack.copy())
      }
      context.pause(0.25)
      result(numberToCopy)
    })
  }

  private def indexOf(needle: String, offset: Int = 0): Int = {
    for (slot <- 0 until data.getSlots) {
      val stack = stackInSlot(slot)
      if (stack != null && hashOf(stack) == needle) return slot + offset
    }
    -1
  }

  /**
   * 物品堆的内容哈希（原 `Hashing.sha256().hashBytes(NbtIo.compress(stack))`）。
   *
   * 1.21.1 的 `NbtIo` 已没有 `compress` 便捷方法，这里用
   * [[StackSerializer.toTag]] 得到物品 NBT，再用 `NbtIo.writeCompressed` 写出等价的
   * gzip 压缩字节流，因此对同一物品产生的哈希与旧实现一致。
   */
  private def hashOf(stack: ItemStack): String = {
    val buffer = new ByteArrayOutputStream()
    NbtIo.writeCompressed(StackSerializer.toTag(stack), buffer)
    Hashing.sha256().hashBytes(buffer.toByteArray).toString
  }
}
