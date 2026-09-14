package li.cil.oc.common.event

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.client.Textures
import li.cil.oc.common.EventHandler
import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.util.PlayerUtils
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

/**
 * 纳米机器：控制器生命周期（创建 / 重建 / 卸载）与 HUD 电量条。
 *
 * 1.21.1 迁移要点：
 *  - `@SubscribeEvent` → 显式 `addListener`；客户端 HUD 放在 [[Client]] 子对象里，
 *    只有物理客户端才注册。
 *  - `RenderGameOverlayEvent.Post`（带 `ElementType.TEXT` 判定）→
 *    `RenderGuiEvent.Post` + `GuiGraphics`：`ScaledResolution` → `GuiGraphics#guiWidth/guiHeight`，
 *    `Tessellator` + `bindTexture` → `GuiGraphics#blit`。
 *  - `LivingEvent.LivingUpdateEvent` → `PlayerTickEvent.Pre`。
 *  - `PlayerEvent.SaveToFile` / `LoadFromFile` 在 1.21.1 已被移除（玩家数据由
 *    `PlayerDataStorage` 统一处理）。原实现把控制器状态写进玩家目录下的 `ocnm` 文件，
 *    现在改为写进玩家的持久化数据（`Player#getPersistentData`，键
 *    `oc:nanomachines`），并在玩家第一次 tick 时读回。
 *  - 因此不再需要 `NbtIo` 文件读写。
 */
object NanomachinesHandler {
  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    Common.initialize()
    if (FMLEnvironment.dist.isClient) Client.initialize()
  }

  /** HUD：只在物理客户端注册。 */
  object Client {
    def initialize(): Unit = {
      NeoForge.EVENT_BUS.addListener((e: RenderGuiEvent.Post) => onRenderGameOverlay(e))
    }

    def onRenderGameOverlay(e: RenderGuiEvent.Post): Unit = {
      val mc = Minecraft.getInstance
      val player = mc.player
      if (player == null) return
      api.Nanomachines.getController(player) match {
        case controller: Controller =>
          val graphics = e.getGuiGraphics
          val sizeX = 8
          val sizeY = 12
          val width = graphics.guiWidth()
          val height = graphics.guiHeight()
          val (x, y) = Settings.get.nanomachineHudPos
          val left =
            math.min(width - sizeX,
              if (x < 0) width / 2 - 91 - 12
              else if (x < 1) width * x
              else x)
          val top =
            math.min(height - sizeY,
              if (y < 0) height - 39
              else if (y < 1) y * height
              else y)
          val size = controller.getLocalBufferSize
          val fill = if (size <= 0) 0.0 else controller.getLocalBuffer / size

          graphics.blit(Textures.overlayNanomachines, left.toInt, top.toInt, 0f, 0f, sizeX, sizeY, sizeX, sizeY)

          // 电量条自下往上填充（与 1.7.10 版本一致）。
          val barHeight = math.max(0, math.min(sizeY, (sizeY * fill).toInt))
          if (barHeight > 0) {
            graphics.blit(Textures.overlayNanomachinesBar,
              left.toInt, top.toInt + sizeY - barHeight,
              0f, (sizeY - barHeight).toFloat,
              sizeX, barHeight, sizeX, sizeY)
          }
        case _ => // 没有可显示的内容。
      }
    }
  }

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
