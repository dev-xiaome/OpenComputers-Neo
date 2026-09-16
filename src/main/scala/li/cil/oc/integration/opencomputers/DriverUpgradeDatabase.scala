package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.inventory.DatabaseInventory
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import net.minecraft.world.item.ItemStack

object DriverUpgradeDatabase extends Item with api.driver.item.HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.DatabaseUpgradeTier1),
    api.Items.get(Constants.ItemName.DatabaseUpgradeTier2),
    api.Items.get(Constants.ItemName.DatabaseUpgradeTier3))

  override def createEnvironment(stack: ItemStack, host: api.network.EnvironmentHost) =
    if (host.world != null && host.world.isClientSide) null
    else new component.UpgradeDatabase(new DatabaseInventory {
      override def container = stack

      // TODO(port): 1.7.10 在这里覆写 `IInventory#isUseableByPlayer(player) = false`
      // （数据库升级挂在玩家手上，不做距离判定）。1.21.1 的
      // `common.inventory.DatabaseInventory` 已不再继承任何带该方法的接口
      // （物品栏统一走 NeoForge 的 `IItemHandler`，没有 `isUseableByPlayer`），
      // 因此这个覆写在 Scala 2.13 下是 "overrides nothing"，直接删除。
      // 等价的「玩家能否继续操作」判定现在由菜单层承担：
      // `common.container.Player#isUseableByPlayer`（经 `stillValid`），
      // 物品宿主时退化为 `player == playerInventory.player`。
    })

  override def slot(stack: ItemStack) = Slot.Upgrade

  override def tier(stack: ItemStack) =
    Delegator.subItem(stack) match {
      case Some(database: item.UpgradeDatabase) => database.tier
      case _ => Tier.One
    }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[component.UpgradeDatabase]
      else null
  }

}
