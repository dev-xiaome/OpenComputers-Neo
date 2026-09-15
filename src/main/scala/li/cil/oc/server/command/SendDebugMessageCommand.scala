package li.cil.oc.server.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import li.cil.oc.api.Network
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.server.network.DebugNetwork
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

/**
 * `/oc_sendDebugMessage <destinationAddress> [message...]`（别名 `/oc_sdbg`）：
 * 向某个调试卡隧道发送一条消息，用于在没有计算机的情况下测试调试卡。
 *
 * 1.21.1 迁移要点：`source.getCommandSenderName` → `source.getTextName`；
 * 参数改用 Brigadier 的 `word()`（目标地址）+ `greedyString()`（消息正文，可含空格）。
 */
object SendDebugMessageCommand extends SimpleCommand("oc_sendDebugMessage") {
  aliases += "oc_sdbg"

  private final val RequiredPermissionLevel = 2

  override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
    builder.requires(source => hasOpLevel(source, RequiredPermissionLevel))
    builder.then(
      RequiredArgumentBuilder.argument[CommandSourceStack, String]("destination", StringArgumentType.word())
        .executes(context => {
          send(context.getSource,
            StringArgumentType.getString(context, "destination"),
            Array.empty[String])
          1
        })
        .then(
          RequiredArgumentBuilder.argument[CommandSourceStack, String]("message", StringArgumentType.greedyString())
            .executes(context => {
              // 与 1.7.10 一致：消息正文按空格切分后作为多个参数传给网络包。
              send(context.getSource,
                StringArgumentType.getString(context, "destination"),
                StringArgumentType.getString(context, "message").split(" "))
              1
            })))
  }

  private def send(source: CommandSourceStack, destination: String, args: Array[String]): Unit = {
    DebugNetwork.getEndpoint(destination) match {
      case Some(endpoint) =>
        val packet = Network.newPacket(source.getTextName, destination, 0, args.toList.toArray[AnyRef])
        endpoint.receivePacket(packet)
      case _ =>
        source.sendSystemMessage(Component.literal(s"No debug card with address '$destination' found."))
    }
  }
}
