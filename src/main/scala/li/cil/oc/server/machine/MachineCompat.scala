package li.cil.oc.server.machine

import li.cil.oc.OpenComputers
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.server.ServerLifecycleHooks

/**
 * 1.21.1 / NeoForge 下的“游戏状态”适配层。
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
   * 集成服务器里游戏是否处于“暂停菜单”状态。
   *
   * TODO(server.client): 上游通过客户端 `Minecraft#isGamePaused` 判断；
   * 客户端包尚未移植，这里退化为 `MinecraftServer#isPaused`
   * （原版只在单人存档下会返回 true，语义与上游一致）。
   */
  def isGamePaused: Boolean = {
    val s = server
    s != null && s.isPaused
  }
}
