package li.cil.oc.integration.opencomputers

import li.cil.oc.{Constants, api}
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.{EnvironmentHost, ManagedEnvironment}
import li.cil.oc.common.Slot
import li.cil.oc.server.component
import li.cil.oc.server.component.CapacitorMountable
import net.minecraft.world.item.ItemStack

object DriverCapacitorMountable extends Item with HostAware {
  override def worksWith(stack: ItemStack): Boolean =
    isOneOf(stack, api.Items.get(Constants.ItemName.CapacitorMountable))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = host match {
    case rack: api.internal.Rack => new CapacitorMountable(rack)
    case _ => null
  }

  override def slot(stack: ItemStack): String = Slot.RackMountable
}
