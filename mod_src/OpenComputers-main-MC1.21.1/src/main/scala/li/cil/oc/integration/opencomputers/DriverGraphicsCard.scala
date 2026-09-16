package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.server.component
import net.minecraft.world.item.ItemStack

object DriverGraphicsCard extends Item with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.GraphicsCardTier1),
    api.Items.get(Constants.ItemName.GraphicsCardTier2),
    api.Items.get(Constants.ItemName.GraphicsCardTier3),
    api.Items.get(Constants.ItemName.GraphicsCardTier4))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.getEnvironmentLevel != null && host.getEnvironmentLevel.isClientSide) null
    else tier(stack) match {
      case Tier.One => new component.GraphicsCard(Tier.One)
      case Tier.Two => new component.GraphicsCard(Tier.Two)
      case Tier.Three => new component.GraphicsCard(Tier.Three)
      case Tier.Four => new component.GraphicsCard(Tier.Four)
      case _ => null
    }

  override def slot(stack: ItemStack) = Slot.Card

  override def tier(stack: ItemStack) = {
    if (isOneOf(stack, api.Items.get(Constants.ItemName.GraphicsCardTier4))) {
      Tier.Four
    } else if (isOneOf(stack, api.Items.get(Constants.ItemName.GraphicsCardTier3))) {
      Tier.Three
    } else if (isOneOf(stack, api.Items.get(Constants.ItemName.GraphicsCardTier2))) {
      Tier.Two
    } else {
      Tier.One
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[component.GraphicsCard]
      else null
  }

}
