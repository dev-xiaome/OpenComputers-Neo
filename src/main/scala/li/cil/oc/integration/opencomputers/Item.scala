package li.cil.oc.integration.opencomputers

import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DriverItem
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.common.Tier
import li.cil.oc.server.driver.Registry
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item

import scala.annotation.tailrec

trait Item extends DriverItem {
  def worksWith(stack: ItemStack, host: Class[_ <: EnvironmentHost]): Boolean =
    worksWith(stack) && !Registry.blacklist.exists {
      case (blacklistedStack, blacklistedHost) =>
        ItemStack.isSameItem(stack, blacklistedStack) &&
          blacklistedHost.exists(_.isAssignableFrom(host))
    }

  override def tier(stack: ItemStack) = Tier.One

  override def dataTag(stack: ItemStack): CompoundTag = Item.dataTag(stack)

  /// 1.21.1：`api.Items.get(stack)` 是**反查**（ItemStack → ItemInfo）。方块物品一旦反查失败，
  /// 所有 `worksWith(stack)` 都会变成 false，driver 查找随之返回 null —— 屏幕上表现为
  /// `Driver.driverFor(...)` 返回 null 的 NPE。这里补一条正向比较兜底。
  protected def isOneOf(stack: ItemStack, items: api.detail.ItemInfo*): Boolean = {
    val targets = items.filter(_ != null)
    if (targets.isEmpty) return false
    val own = api.Items.get(stack)
    if (own != null && targets.contains(own)) true
    else targets.exists { info =>
      val candidate = info.createItemStack(1)
      !candidate.isEmpty && ItemStack.isSameItem(candidate, stack)
    }
  }

  protected def isAdapter(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Adapter].isAssignableFrom(host)

  protected def isComputer(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Case].isAssignableFrom(host)

  protected def isRobot(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Robot].isAssignableFrom(host)

  protected def isRotatable(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Rotatable].isAssignableFrom(host)

  protected def isServer(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Server].isAssignableFrom(host)

  protected def isTablet(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Tablet].isAssignableFrom(host)

  protected def isMicrocontroller(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Microcontroller].isAssignableFrom(host)

  protected def isDrone(host: Class[_ <: EnvironmentHost]): Boolean = classOf[internal.Drone].isAssignableFrom(host)
}

object Item {
  def dataTag(stack: ItemStack): CompoundTag = {
    val nbt = stack.getOrCreateTag
    if (!nbt.contains(Settings.namespace + "data")) {
      nbt.put(Settings.namespace + "data", new CompoundTag())
    }
    nbt.getCompound(Settings.namespace + "data")
  }

  @tailrec
  private def getTag(tagCompound: CompoundTag, keys: Array[String]): Option[CompoundTag] = {
    if (keys.length == 0) Option(tagCompound)
    else if (!tagCompound.contains(keys(0))) None
    else getTag(tagCompound.getCompound(keys(0)), keys.drop(1))
  }

  private def getTag(stack: ItemStack, keys: Array[String]): Option[CompoundTag] = {
    if (stack == null || stack.getCount == 0 || stack == ItemStack.EMPTY) None
    else if (!stack.hasTag) None
    else getTag(stack.getTag, keys)
  }

  def address(stack: ItemStack): Option[String] = {
    val addressKey = "address"
    getTag(stack, Array(Settings.namespace + "data", "node")) match {
      case Some(tag) if tag.contains(addressKey) => Option(tag.getString(addressKey))
      case _ => None
    }
  }
}
