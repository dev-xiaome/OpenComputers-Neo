package li.cil.oc.common.nanomachines

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.BehaviorProvider
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.util.PlayerUtils
import net.minecraft.world.entity.player.Player

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * 纳米机器的全局状态与 API 入口（对应 1.7.10 的 `common.nanomachines.Nanomachines`）。
 *
 * 1.21.1 迁移要点：
 *  - `EntityPlayer#getEntityWorld` → `Player#level()`；`World#isRemote` → `Level#isClientSide`
 *  - 持久化数据继续走 [[li.cil.oc.util.PlayerUtils#persistedData]]（内部使用
 *    `Player#getPersistentData`），键名保持 `Settings.namespace + "hasNanomachines"` 不变，
 *    以兼容旧存档。
 *  - 配置同步原本调用 `server.PacketSender.sendNanomachineConfiguration`（尚未移植），
 *    这里降级为「客户端受限的配置变更不广播」，见 [[uninstallController]] 的 TODO。
 */
object Nanomachines extends api.detail.NanomachinesAPI {
  val providers = mutable.Set.empty[BehaviorProvider]

  val serverControllers = mutable.WeakHashMap.empty[Player, ControllerImpl]
  val clientControllers = mutable.WeakHashMap.empty[Player, ControllerImpl]

  def controllers(player: Player) = if (player.level().isClientSide) clientControllers else serverControllers

  override def addProvider(provider: BehaviorProvider): Unit = providers += provider

  override def getProviders: java.lang.Iterable[BehaviorProvider] = providers.asJava

  override def getController(player: Player): Controller = {
    if (hasController(player)) controllers(player).getOrElseUpdate(player, new ControllerImpl(player))
    else null
  }

  override def hasController(player: Player) = {
    PlayerUtils.persistedData(player).getBoolean(Settings.namespace + "hasNanomachines")
  }

  override def installController(player: Player): Controller = {
    if (!hasController(player)) {
      PlayerUtils.persistedData(player).putBoolean(Settings.namespace + "hasNanomachines", true)
    }
    getController(player) // 初始化控制器实例。
  }

  override def uninstallController(player: Player): Unit = {
    getController(player) match {
      case controller: ControllerImpl =>
        controller.dispose()
        controllers(player) -= player
        PlayerUtils.persistedData(player).remove(Settings.namespace + "hasNanomachines")
        // TODO(server.packet): 原实现调用 `PacketSender.sendNanomachineConfiguration(player)`
        // 把控制器配置同步给附近客户端（`server.PacketSender` 尚未移植）。
      case _ => // 本来就没有控制器。
    }
  }
}
