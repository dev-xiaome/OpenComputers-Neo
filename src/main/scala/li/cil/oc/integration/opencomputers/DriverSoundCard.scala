package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.server.component.SoundCard
import net.minecraft.world.item.ItemStack

object DriverSoundCard extends Item with HostAware {

  override def worksWith(stack: ItemStack): Boolean =
    isOneOf(stack, api.Items.get(Constants.ItemName.SoundCardTier1))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.getEnvironmentLevel != null && host.getEnvironmentLevel.isClientSide) null
    else new SoundCard(host)

  override def slot(stack: ItemStack) = Slot.Card

  override def tier(stack: ItemStack) = Tier.One

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack)) classOf[SoundCard] else null
  }
}