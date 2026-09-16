package li.cil.oc.integration.opencomputers
import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.server.component
import li.cil.oc.util.ExtendedInventory._
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

object DriverDiskDriveMountable extends Item with HostAware {
  override def worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.ItemName.DiskDriveMountable))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = host match {
    case rack: api.internal.Rack => new component.DiskDriveMountable(rack, rack.indexOf(stack))
    case _ => null // Welp.
  }

  override def slot(stack: ItemStack): String = Slot.RackMountable

  override def dataTag(stack: ItemStack): CompoundTag = {
    if (!stack.hasTag()) {
      // 1.21.1：`ItemStack` 没有 `put`，NBT 走 `li.cil.oc` 的隐式类 → `setTag`。
      stack.setTag(new CompoundTag())
    }
    stack.getTag()
  }
}
