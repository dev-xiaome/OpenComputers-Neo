package li.cil.oc.server.command

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import li.cil.oc.Settings
import li.cil.oc.common.command.SimpleCommand
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

/**
 * `/oc_renderWirelessNetwork [<boolean>]`（别名 `/oc_wlan`）：开关无线网络的
 * R 树调试渲染（原 `Settings.rTreeDebugRenderer`）。
 *
 * 与 1.7.10 版一致：不带参数时切换当前值；参数解析改用 Brigadier 的 [[BoolArgumentType]]。
 */
object WirelessRenderingCommand extends SimpleCommand("oc_renderWirelessNetwork") {
  aliases += "oc_wlan"

  private final val RequiredPermissionLevel = 2

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
    Settings.rTreeDebugRenderer = value
    source.sendSystemMessage(Component.literal(
      s"Wireless network rendering is now ${if (value) "enabled" else "disabled"}."))
  }

  private def toggle(source: CommandSourceStack): Unit = set(source, !Settings.rTreeDebugRenderer)
}
