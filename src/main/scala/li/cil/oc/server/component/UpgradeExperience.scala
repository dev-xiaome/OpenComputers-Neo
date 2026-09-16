package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import net.minecraft.core.Holder
import net.minecraft.world.item.enchantment.{Enchantment, EnchantmentHelper, ItemEnchantments}
import net.minecraft.world.item.Items
import net.minecraft.nbt.CompoundTag

import scala.jdk.CollectionConverters._

class UpgradeExperience(val host: EnvironmentHost with internal.Agent) extends prefab.ManagedEnvironment with DeviceInfo {
  final val MaxLevel = 30

  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("experience").
    withConnector(30 * Settings.get.bufferPerLevel).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Knowledge database",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "ERSO (Event Recorder and Self-Optimizer)",
    DeviceAttribute.Capacity -> "30"
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  var experience = 0.0

  var level = 0

  def xpForLevel(level: Int): Double =
    if (level == 0) 0
    else Settings.get.baseXpToLevel + Math.pow(level * Settings.get.constantXpGrowth, Settings.get.exponentialXpGrowth)

  def xpForNextLevel = xpForLevel(level + 1)

  def addExperience(value: Double): Unit = {
    if (level < MaxLevel) {
      experience = experience + value
      if (experience >= xpForNextLevel) {
        updateXpInfo()
      }
    }
  }

  def updateXpInfo(): Unit = {
    // xp(level) = base + (level * const) ^ exp
    // pow(xp(level) - base, 1/exp) / const = level
    level = math.min((Math.pow(experience - Settings.get.baseXpToLevel, 1 / Settings.get.exponentialXpGrowth) / Settings.get.constantXpGrowth).toInt, 30)
    if (node != null) {
      node.setLocalBufferSize(Settings.get.bufferPerLevel * level)
    }
  }

  @Callback(direct = true, doc = """function():number -- The current level of experience stored in this experience upgrade.""")
  def level(context: Context, args: Arguments): Array[AnyRef] = {
    val xpNeeded = xpForNextLevel - xpForLevel(level)
    val xpProgress = math.max(0, experience - xpForLevel(level))
    result(level + xpProgress / xpNeeded)
  }

  @Callback(doc = """function():boolean -- Tries to consume an enchanted item to add experience to the upgrade.""")
  def consume(context: Context, args: Arguments): Array[AnyRef] = {
    if (level >= MaxLevel) {
      return result((), "max level")
    }
    // 1.21.1：`IItemHandler` 的空槽返回 `ItemStack.EMPTY` 而不是 `null`，
    // 且 `ItemStack#stackSize` 变成 `getCount`。
    val stack = host.mainInventory.getStackInSlot(host.selectedSlot)
    if (stack == null || stack.isEmpty || stack.getCount < 1) {
      return result((), "no item")
    }
    var xp = 0
    // 1.21.1：`Items.experience_bottle` → `Items.EXPERIENCE_BOTTLE`（物品字段全部大写），
    // `Level#rand` → `Level#random`。
    if (stack.is(Items.EXPERIENCE_BOTTLE)) {
      xp += 3 + host.world.random.nextInt(5) + host.world.random.nextInt(5)
    }
    else {
      // 1.21.1：附魔改为数据组件 `ItemEnchantments`：
      //   `EnchantmentHelper.getEnchantments(stack)`（返回 id → 等级 的 Map）
      //   → `stack.getEnchantments`（返回 `ItemEnchantments`，可遍历 `Holder[Enchantment]` → 等级）；
      //   `Enchantment.enchantmentsList(id)` 已移除，直接取 `Holder#value`；
      //   `Enchantment#getMinEnchantability(level)` → `getMinCost(level)`。
      val enchantments: ItemEnchantments = stack.getEnchantments
      for (entry <- enchantments.entrySet.asScala) {
        val holder: Holder[Enchantment] = entry.getKey
        val enchantmentLevel: Int = entry.getIntValue
        val enchantment = if (holder == null) null else holder.value()
        if (enchantment != null) {
          xp += enchantment.getMinCost(enchantmentLevel)
        }
      }
      if (xp <= 0) {
        return result((), "could not extract experience from item")
      }
    }
    // 1.21.1：`IInventory#decrStackSize(slot, n)` → `IItemHandler#extractItem(slot, n, simulate)`。
    val consumed = host.mainInventory.extractItem(host.selectedSlot, 1, false)
    if (consumed == null || consumed.isEmpty || consumed.getCount < 1) {
      return result((), "could not consume item")
    }
    addExperience(xp * Settings.get.constantXpGrowth)
    result(true)
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.putDouble(Settings.namespace + "xp", experience)
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    experience = nbt.getDouble(Settings.namespace + "xp") max 0
    updateXpInfo()
  }
}
