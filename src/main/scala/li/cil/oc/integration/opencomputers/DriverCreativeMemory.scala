package li.cil.oc.integration.opencomputers

import li.cil.oc.{Constants, api}
import li.cil.oc.api.driver.item.{CallBudget, Memory}
import li.cil.oc.api.network.{EnvironmentHost, ManagedEnvironment}
import li.cil.oc.common.Slot
import li.cil.oc.common.init.Items
import li.cil.oc.server.component
import li.cil.oc.server.component.CreativeMemory
import net.minecraft.world.item.ItemStack

object DriverCreativeMemory extends Item with Memory with CallBudget {
  override def amount(stack: ItemStack): Double = Double.PositiveInfinity

  override def getCallBudget(stack: ItemStack): Double = 2.0

  override def worksWith(stack: ItemStack): Boolean = isOneOf(stack, Items.get(Constants.ItemName.RAMCreative))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = new CreativeMemory

  override def slot(stack: ItemStack): String = Slot.Memory
}
