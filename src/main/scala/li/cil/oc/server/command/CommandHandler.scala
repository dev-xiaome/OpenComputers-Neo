package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import li.cil.oc.OpenComputers
import net.minecraft.commands.CommandSourceStack
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * 命令注册入口。
 *
 * 1.7.10 走 `FMLServerStartingEvent#registerServerCommand`（一次注册 8 个 `ICommand`）；
 * 1.21.1 改为监听 NeoForge 的 `RegisterCommandsEvent`，把 Brigadier 命令树挂到
 * `CommandDispatcher` 上。命令清单与 1.7.10 完全一致（见 [[commands]]）。
 *
 * 接线方式：`li.cil.oc.server.Proxy` 在预初始化时调用一次 [[initialize]]。
 * {{{
 *   NeoForge.EVENT_BUS.addListener((e: RegisterCommandsEvent) =>
 *     li.cil.oc.server.command.CommandHandler.register(e))
 * }}}
 */
object CommandHandler {

  /** 所有需要注册的命令（顺序与 1.7.10 的注册顺序一致）。 */
  private val commands = Seq(
    DebugNanomachinesCommand,
    LogNanomachinesCommand,
    NetworkProfilingCommand,
    NonDisassemblyAgreementCommand,
    WirelessRenderingCommand,
    SpawnComputerCommand,
    DebugWhitelistCommand,
    SendDebugMessageCommand
  )

  /** 是否已经挂过事件监听。 */
  private var initialized = false

  /** 把全部命令注册进给定的分发器（主名 + 别名各生成一个根节点）。 */
  def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit = {
    for (command <- commands; builder <- command.builders) {
      try {
        dispatcher.register(builder)
      }
      catch {
        case t: Throwable => OpenComputers.log.warn(s"Failed registering command '${command.name}'.", t)
      }
    }
    OpenComputers.log.debug(s"Registered ${commands.size} OpenComputers commands.")
  }

  /** 处理 `RegisterCommandsEvent`（NeoForge 1.21.1 的服务器命令注册时机）。 */
  def register(e: RegisterCommandsEvent): Unit = register(e.getDispatcher)

  /**
   * 在 NeoForge 事件总线上挂上 `RegisterCommandsEvent` 监听。
   *
   * 加了幂等保护：重复挂同一个监听会让 Brigadier 在同一分发器上重复注册同名命令，
   * 结果是整批命令注册失败（Brigadier 对重名命令直接抛异常）。
   */
  def initialize(): Unit = synchronized {
    if (!initialized) {
      initialized = true
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        (e: RegisterCommandsEvent) => register(e))
    }
  }
}
