package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.component.{RackKVM, TerminalServer}
import li.cil.oc.util.ExtendedInventory._
import net.minecraft.world.item.ItemStack

object DriverTerminalServer extends Item with HostAware {
  override def worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.ItemName.TerminalServer),
    api.Items.get(Constants.ItemName.RackKVM))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = host match {
    case rack: api.internal.Rack if api.Items.get(stack) == api.Items.get(Constants.ItemName.RackKVM) => new RackKVM(rack, rack.indexOf(stack))
    case rack: api.internal.Rack => new TerminalServer(rack, rack.indexOf(stack))
    case _ => null
  }

  override def slot(stack: ItemStack): String = Slot.RackMountable
}
