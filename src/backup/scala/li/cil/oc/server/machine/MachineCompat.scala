package li.cil.oc.server.machine

import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.server.ServerLifecycleHooks

/**
 * 1.21.1 / NeoForge 下的「游戏状态」适配层。
 *
 * 1.7.10 里机器代码依赖 `MinecraftServer.getServer`、`DimensionManager`
 * 等静态入口；NeoForge 把这些换成了 `ServerLifecycleHooks` 与 `ServerLevel`
 * 上的实例方法，统一收敛到这里，避免各处硬编码。
 */
private[machine] object MachineCompat {

  /** 当前服务器实例；未启动时返回 `null`（对应原 `MinecraftServer.getServer`）。 */
  def server: MinecraftServer = ServerLifecycleHooks.getCurrentServer

  /** 是否为单人（集成服务器）存档。 */
  def isSinglePlayer: Boolean = {
    val s = server
    s != null && s.isSingleplayer
  }

  /**
   * 集成服务器里游戏是否处于「暂停菜单」状态。
   *
   * 上游（CE-1.20 `server/machine/Machine.scala`）的写法是
   * `server != null && !server.isDedicatedServer && SinglePlayerPause.isPaused`，
   * 其中 `li.cil.oc.common.SinglePlayerPause` 是 common 包里一个只有 4 行的小对象，
   * 由 `common/EventHandler` 在客户端的暂停 / 恢复事件里置位。它对应 1.7.10 的
   * `Minecraft.getMinecraft.isGamePaused`（客户端才有的概念）。
   *
   * TODO(common.SinglePlayerPause): 本项目 common 包目前**没有** `SinglePlayerPause`，
   * `common/EventHandler.scala` 也还没有 `setSinglePlayerPause` 这一段，因此先退化为
   * 服务端的 `MinecraftServer#isPaused`：该方法在 `DedicatedServer` 上恒为 `false`，
   * 只有 `IntegratedServer` 会真实反映暂停菜单，判定结果与上游一致。
   * common 包把 `SinglePlayerPause` 补齐后，请把这里换成
   * `s != null && !s.isDedicatedServer && li.cil.oc.common.SinglePlayerPause.isPaused`。
   */
  def isGamePaused: Boolean = {
    val s = server
    s != null && !s.isDedicatedServer && s.isPaused
  }
}
