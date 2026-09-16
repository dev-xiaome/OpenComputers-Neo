package li.cil.oc.common.event

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.common.EventHandler
import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.util.PlayerUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

/**
 * 纳米机器：控制器生命周期（创建 / 重建 / 卸载）。
 *
 * ==与客户端部分的分工==
 * 1.7.10 里 HUD 电量条是 `NanomachinesHandler.Client` 子对象；1.21.1 要求 `common` 包
 * 不能有对 `client` 包的编译期引用（否则整个 `client` 会被拖进编译集），因此 HUD 已整体
 * 搬到 `li.cil.oc.client.NanomachineHud`（由 `li.cil.oc.client.ClientListeners` 注册），
 * 本对象只保留**双端通用**的控制器生命周期。
 *
 * 1.21.1 迁移要点：
 *  - `@SubscribeEvent` → 显式 `addListener`。
 *  - `LivingEvent.LivingUpdateEvent` → `PlayerTickEvent.Pre`。
 *  - `PlayerEvent.SaveToFile` / `LoadFromFile` 在 1.21.1 已被移除（玩家数据由
 *    `PlayerDataStorage` 统一处理）。原实现把控制器状态写进玩家目录下的 `ocnm` 文件，
 *    现在改为写进玩家的持久化数据（`Player#getPersistentData`，键
 *    `oc:nanomachines`），并在玩家第一次 tick 时读回。
 *  - 因此不再需要 `NbtIo` 文件读写。
 */
object NanomachinesHandler {
  /** 注册监听器；由 [[EventHandlers]] 调用一次。 */
  def initialize(): Unit = Common.initialize()

  /** 服务端 / 客户端通用的控制器生命周期。 */
  object Common {
    /** 已经读过一次持久化状态的玩家；键是弱引用，玩家重登后会重新读取。 */
    private val loadedState = java.util.Collections.newSetFromMap(new java.util.WeakHashMap[Player, java.lang.Boolean])

    private def stateKey = Settings.namespace + "nanomachines"

    def initialize(): Unit = {
      NeoForge.EVENT_BUS.addListener((e: PlayerEvent.PlayerRespawnEvent) => onPlayerRespawn(e))
      NeoForge.EVENT_BUS.addListener((e: PlayerTickEvent.Pre) => onPlayerTick(e))
      NeoForge.EVENT_BUS.addListener((e: PlayerEvent.PlayerLoggedOutEvent) => onPlayerDisconnect(e))
    }

    def onPlayerRespawn(e: PlayerEvent.PlayerRespawnEvent): Unit = {
      api.Nanomachines.getController(e.getEntity) match {
        case controller: Controller => controller.changeBuffer(-controller.getLocalBuffer)
        case _ => // 不是带纳米机器的玩家。
      }
    }

    def onPlayerTick(e: PlayerTickEvent.Pre): Unit = {
      e.getEntity match {
        case player: Player => api.Nanomachines.getController(player) match {
          case controller: ControllerImpl =>
            if (controller.player eq player) {
              // 1.21.1 没有玩家数据读档事件，改为每个玩家实例第一次 tick 时读回状态。
              if (loadedState.add(player)) {
                loadState(player, controller)
              }
              controller.update()
            }
            else {
              // 玩家实体实例变了（例如重生），重建控制器。
              val nbt = new CompoundTag()
              controller.save(nbt)
              api.Nanomachines.uninstallController(controller.player)
              api.Nanomachines.installController(player) match {
                case newController: ControllerImpl =>
                  newController.load(nbt)
                  newController.reset()
                case _ => // 不该发生。
              }
            }
          case _ => // 不是带纳米机器的玩家。
        }
        case _ => // 不是玩家。
      }
    }

    def onPlayerDisconnect(e: PlayerEvent.PlayerLoggedOutEvent): Unit = {
      e.getEntity match {
        case player: Player => api.Nanomachines.getController(player) match {
          case controller: ControllerImpl =>
            saveState(player, controller)
            // 等一个 tick，因为保存发生在这个事件之后。
            EventHandler.scheduleServer(() => api.Nanomachines.uninstallController(player))
          case _ => // 不是带纳米机器的玩家。
        }
        case _ =>
      }
    }

    /** 把控制器状态写进玩家持久化数据（取代 1.7.10 的 `ocnm` 文件）。 */
    private def saveState(player: Player, controller: ControllerImpl): Unit = {
      try {
        val nbt = new CompoundTag()
        controller.save(nbt)
        PlayerUtils.persistedData(player).put(stateKey, nbt)
      }
      catch {
        case t: Throwable =>
          OpenComputers.log.warn("Error saving nanomachine state.", t)
      }
    }

    /** 从玩家持久化数据里读回控制器状态。 */
    private def loadState(player: Player, controller: ControllerImpl): Unit = {
      val data = PlayerUtils.persistedData(player)
      if (data.contains(stateKey)) {
        try controller.load(data.getCompound(stateKey)) catch {
          case t: Throwable =>
            OpenComputers.log.warn("Error loading nanomachine state.", t)
        }
      }
    }
  }
}
