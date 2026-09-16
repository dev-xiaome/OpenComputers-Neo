package li.cil.oc.server.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import li.cil.oc.api
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

/**
 * `/oc_debugNanomachines`（别名 `/oc_dn`）：给自己安装一个调试用的纳米机器控制器，
 * 并把配置映射写入日志。
 *
 * 1.21.1 迁移要点：1.7.10 用 `ICommandSender` 模式匹配拿 `EntityPlayer`；
 * 1.21.1 直接从 `CommandSourceStack#getPlayer` 取玩家（控制台时为 `null`），
 * 非玩家执行时报 Brigadier 的 `CommandSyntaxException`。
 */
object DebugNanomachinesCommand extends SimpleCommand("oc_debugNanomachines") {
  aliases += "oc_dn"

  private final val RequiredPermissionLevel = 2

  override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
    builder.requires(source => hasOpLevel(source, RequiredPermissionLevel))
    builder.executes(context => {
      val player = context.getSource.getPlayer
      if (player == null) {
        // 等价于 1.7.10 的 WrongUsageException：Brigadier 没有
        // `CommandSyntaxException.invalidInput`，用 `SimpleCommandExceptionType` 构造带消息的语法异常。
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
          Component.literal("Can only be used by players.")).create()
      }
      api.Nanomachines.installController(player) match {
        case controller: ControllerImpl =>
          controller.debug()
          player.sendSystemMessage(Component.literal("Debug configuration created, see log for mappings."))
        case _ => // Someone did something.
      }
      1
    })
  }
}
