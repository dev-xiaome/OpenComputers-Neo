package li.cil.oc.integration.opencomputers
import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.{EnvironmentHost, ManagedEnvironment}
import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.server.component
import li.cil.oc.util.BlockPosition
import li.cil.oc.{Constants, Settings, api}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.core.Direction
import net.neoforged.neoforge.server.ServerLifecycleHooks

/**
  * @author Vexatos
  */
object DriverUpgradeMF extends Item with HostAware {
  override def worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.ItemName.MFU))

  override def worksWith(stack: ItemStack, host: Class[_ <: EnvironmentHost]): Boolean =
    worksWith(stack) && isAdapter(host)

  override def slot(stack: ItemStack): String = Slot.Upgrade

  override def tier(stack: ItemStack) = Tier.Three

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = {
    if (host.world != null && !host.world.isClientSide) {
      if (stack.hasTag()) {
        stack.getTag().getIntArray(Settings.namespace + "coord") match {
          case Array(x, y, z, dim, side) =>
            Option(worldForLegacyDimension(dim)) match {
              // 1.21.1 没有 `Direction.getOrientation`，改用 `Direction.from3DDataValue`。
              case Some(world) => return new component.UpgradeMF(host, BlockPosition(x, y, z, world), Direction.from3DDataValue(side))
              case _ => // Invalid dimension ID
            }
          case _ => // Invalid tag
        }
      }
    }
    null
  }

  /**
   * 把 1.7.10 的**数字维度 id** 映射到 1.21.1 的维度。
   *
   * TODO(port): 1.21.1 用注册表键（`ResourceKey[Level]`）标识维度，没有稳定的数字 id，
   * 因此只能还原原版三个维度（-1 下界 / 0 主世界 / 1 末地）。
   * 存档里如果记的是模组维度，需要改成保存维度键字符串才能恢复。
   */
  private def worldForLegacyDimension(id: Int): Level = {
    val server = ServerLifecycleHooks.getCurrentServer
    if (server == null) null
    else id match {
      case -1 => server.getLevel(Level.NETHER)
      case 0 => server.getLevel(Level.OVERWORLD)
      case 1 => server.getLevel(Level.END)
      case _ => null
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[component.UpgradeMF]
      else null
  }

}
