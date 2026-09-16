package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.entity.Drone
import li.cil.oc.server.component
import li.cil.oc.server.component.UpgradeTractorBeam
import net.minecraft.world.item.ItemStack

object DriverUpgradeTractorBeam extends Item with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.TractorBeamUpgrade))

  // TODO(port): 1.7.10 还有一支 `case tablet: TabletWrapper => ...`（平板宿主）。
  // `TabletWrapper` 已随 `common/item/Tablet.scala` 一起降级删除，平板宿主暂时拿不到
  // 「假玩家」供应商，因此该分支被移除；等 `TabletWrapper` 恢复后补回。
  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isClientSide) null
    else host match {
      case drone: Drone => new UpgradeTractorBeam.Drone(drone)
      case robot: Robot => new component.UpgradeTractorBeam.Player(host, robot.player)
      case _ => null
    }

  override def slot(stack: ItemStack) = Slot.Upgrade

  override def tier(stack: ItemStack) = Tier.Three

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[component.UpgradeTractorBeam.Common]
      else null
  }

}
