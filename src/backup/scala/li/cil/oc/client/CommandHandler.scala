package li.cil.oc.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import li.cil.oc.OpenComputers
import li.cil.oc.common.command.SimpleCommand
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * 客户端命令注册入口（对应 1.7.10 的 `li.cil.oc.client.CommandHandler`）。
 *
 * ==1.21.1 与 1.7.10 的结构性差异==
 * 1.7.10 用 `ClientCommandHandler.instance.registerCommand(ICommand)` 注册客户端命令；
 * 1.21.1 里 Forge 的 `ClientCommandHandler` 已经不存在，客户端命令改为挂在 NeoForge 的
 * `RegisterClientCommandsEvent` 上，把 Brigadier 命令树注册进 `CommandDispatcher`。
 * 这与服务端命令（`RegisterCommandsEvent`）同构，区别只有事件类型，以及该事件挂的是
 * **游戏事件总线** `NeoForge.EVENT_BUS`（不是 mod 事件总线）。
 *
 * 接线方式（`client.Proxy#clientSetup` 里已经有一行）：
 * {{{
 *   li.cil.oc.client.CommandHandler.initialize()
 * }}}
 */
object CommandHandler {
  /** 需要注册的客户端命令。 */
  private val commands = Seq(SetClipboardCommand)

  /** 把全部命令注册进给定的分发器（主名 + 别名各生成一个根节点）。 */
  def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit = {
    for (command <- commands; builder <- command.builders) {
      try {
        dispatcher.register(builder)
      }
      catch {
        case t: Throwable => OpenComputers.log.warn(s"Failed registering client command '${command.name}'.", t)
      }
    }
  }

  /** 处理 `RegisterClientCommandsEvent`（NeoForge 1.21.1 的客户端命令注册时机）。 */
  def register(e: RegisterClientCommandsEvent): Unit = register(e.getDispatcher)

  /** 在 NeoForge 事件总线上挂上 `RegisterClientCommandsEvent` 监听。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: RegisterClientCommandsEvent) => register(e))
  }

  /**
   * `/oc_setclipboard <value>`：把一段文本写进系统剪贴板。
   *
   * 语义与 1.7.10 保持一致：
   *  - 1.7.10 检查 `source.getEntityWorld.isRemote`；1.21.1 的客户端命令分发器本身
   *    只在客户端运行，因此改成检查「本地玩家存在」。
   *  - 1.7.10 取 `command(0)`（按词切分、支持引号），这里用 Brigadier 的 `word()`，
   *    含义相同。
   *  - 1.7.10 的 `getRequiredPermissionLevel = 0`：不限制权限；Brigadier 里不调用
   *    `builder.requires` 就等价于「无权限要求」。
   */
  object SetClipboardCommand extends SimpleCommand("oc_setclipboard") {
    // OP levels for reference:
    // 1 - Ops can bypass spawn protection.
    // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
    // 3 - Ops can use /ban, /deop, /kick, and /op.
    // 4 - Ops can use /stop.

    override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
      builder.then(
        RequiredArgumentBuilder.argument[CommandSourceStack, String]("value", StringArgumentType.word())
          .executes(context => {
            setClipboard(StringArgumentType.getString(context, "value"))
            1
          }))
    }
  }

  /** 把文本写进剪贴板；只在本地玩家存在时生效。 */
  private def setClipboard(value: String): Unit = {
    val mc = Minecraft.getInstance()
    if (mc != null && mc.player != null) {
      // 1.7.10：`GuiScreen.setClipboardString(value)`。
      mc.keyboardHandler.setClipboard(value)
    }
  }
}
