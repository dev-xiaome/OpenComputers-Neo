package li.cil.oc.server.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import li.cil.oc.api
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player

/**
 * `/oc_nanomachines [<player>]`（别名 `/oc_nm`）：把某位玩家的纳米机器配置打印到日志。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 用 `MinecraftServer#getConfigurationManager.func_152612_a(name)` 按名字取玩家；
 *    1.21.1 改为 `MinecraftServer#getPlayerList#getPlayerByName`；
 *  - 不带参数时用命令源自己（控制台执行且不给名字时给提示）。
 */
object LogNanomachinesCommand extends SimpleCommand("oc_nanomachines") {
  aliases += "oc_nm"

  private final val RequiredPermissionLevel = 2

  override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
    builder.requires(source => hasOpLevel(source, RequiredPermissionLevel))
    builder.executes(context => {
      logFor(context.getSource, context.getSource.getPlayer)
      1
    })
    builder.then(
      RequiredArgumentBuilder.argument[CommandSourceStack, String]("player", StringArgumentType.word())
        .executes(context => {
          logFor(context.getSource,
            findPlayer(context.getSource.getServer, StringArgumentType.getString(context, "player")))
          1
        }))
  }

  /** 按名字查在线玩家；服务端未就绪或玩家不在线时返回 `null`。 */
  private def findPlayer(server: MinecraftServer, name: String): Player = {
    if (server == null) null
    else server.getPlayerList.getPlayerByName(name)
  }

  private def logFor(source: CommandSourceStack, player: Player): Unit = {
    if (player == null) {
      source.sendSystemMessage(Component.literal("Player entity not found."))
      return
    }
    api.Nanomachines.installController(player) match {
      case controller: ControllerImpl => controller.print()
      case _ => // Someone did something.
    }
  }
}
