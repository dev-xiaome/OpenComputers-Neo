package li.cil.oc.server.command

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import li.cil.oc.common.PacketBuilder
import li.cil.oc.common.command.SimpleCommand
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

/**
 * `/oc_profileNetwork [<boolean>]`（别名 `/oc_pn`）：开关网络包收发日志。
 *
 * 1.7.10 用 `CommandBase#parseBoolean` 手工解析参数；1.21.1 改为 Brigadier 的
 * [[BoolArgumentType]]。为兼容旧的「直接给一个 boolean」用法，参数是可选的：
 * 不给参数就切换当前值。
 */
object NetworkProfilingCommand extends SimpleCommand("oc_profileNetwork") {
  aliases += "oc_pn"

  private final val RequiredPermissionLevel = 3

  override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
    builder.requires(source => hasOpLevel(source, RequiredPermissionLevel))
    builder.executes(context => {
      toggle(context.getSource)
      1
    })
    builder.then(
      RequiredArgumentBuilder.argument[CommandSourceStack, java.lang.Boolean]("value", BoolArgumentType.bool())
        .executes(context => {
          set(context.getSource, BoolArgumentType.getBool(context, "value"))
          1
        }))
  }

  private def set(source: CommandSourceStack, value: Boolean): Unit = {
    PacketBuilder.isProfilingEnabled = value
    source.sendSystemMessage(Component.literal(
      s"Network profiling is now ${if (value) "enabled" else "disabled"}."))
  }

  private def toggle(source: CommandSourceStack): Unit = set(source, !PacketBuilder.isProfilingEnabled)
}
