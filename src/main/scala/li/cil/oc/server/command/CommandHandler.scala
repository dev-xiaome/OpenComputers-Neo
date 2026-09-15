package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import li.cil.oc.OpenComputers
import net.minecraft.commands.CommandSourceStack
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * 命令注册入口。
 *
 * 1.7.10 走 `FMLServerStartingEvent#registerServerCommand`；
 * 1.21.1 改为监听 NeoForge 的 `RegisterCommandsEvent`，直接把命令树挂到
 * `CommandDispatcher` 上（Brigadier）。
 *
 * 接线方式（主类里加一行即可）：
 * {{{
 *   NeoForge.EVENT_BUS.addListener((e: RegisterCommandsEvent) =>
 *     li.cil.oc.server.command.CommandHandler.register(e))
 * }}}
 * 也可以直接调用 [[initialize]]（内部就是上面这句）。
 */
object CommandHandler {

  /** 所有需要注册的命令。 */
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

  /** 在 NeoForge 事件总线上挂上 `RegisterCommandsEvent` 监听。 */
  def initialize(): Unit = {
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
      (e: RegisterCommandsEvent) => register(e))
  }
}
