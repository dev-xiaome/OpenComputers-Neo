package li.cil.oc.integration.opencomputers

import li.cil.oc.{Constants, api}
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.server.component
import net.minecraft.world.item.ItemStack

object DriverQuadGraphicsCard extends Item with HostAware {
  override def worksWith(stack: ItemStack): Boolean =
    isOneOf(stack, api.Items.get(Constants.ItemName.QuadGraphicsCard))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.getEnvironmentLevel != null && host.getEnvironmentLevel.isClientSide) null
    else new component.QuadGraphicsCard

  override def slot(stack: ItemStack): String = Slot.Card

  override def tier(stack: ItemStack): Int = Tier.Four

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack)) classOf[component.QuadGraphicsCard]
      else null
  }
}
