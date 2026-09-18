package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import li.cil.oc.Settings
import li.cil.oc.Settings.DebugCardAccess
import net.minecraft.commands.{CommandSourceStack, Commands}
import net.minecraft.network.chat.Component

object DebugWhitelistCommand {
  private val NotEnabled = new SimpleCommandExceptionType(Component.literal("Debug card whitelisting is not enabled."))

  def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit = {
    val command = Commands.literal("oc_debugWhitelist")
      .then(Commands.literal("revoke")
        .executes(context => revoke(context.getSource, context.getSource.getTextName))
        .then(Commands.argument("player", StringArgumentType.word())
          .requires(_.hasPermission(2))
          .executes(context => revoke(context.getSource, StringArgumentType.getString(context, "player")))))
      .then(Commands.literal("list")
        .requires(_.hasPermission(2))
        .executes(context => list(context.getSource)))
      .then(Commands.literal("add")
        .requires(_.hasPermission(2))
        .then(Commands.argument("player", StringArgumentType.word())
          .executes(context => add(context.getSource, StringArgumentType.getString(context, "player")))))
      .then(Commands.literal("remove")
        .requires(_.hasPermission(2))
        .then(Commands.argument("player", StringArgumentType.word())
          .executes(context => remove(context.getSource, StringArgumentType.getString(context, "player")))))

    dispatcher.register(command)
  }

  private def whitelist = Settings.get.debugCardAccess match {
    case value: DebugCardAccess.Whitelist => value
    case _ => throw NotEnabled.create()
  }

  private def revoke(source: CommandSourceStack, player: String): Int = {
    val wl = whitelist
    if (wl.isWhitelisted(player)) {
      wl.invalidate(player)
      source.sendSuccess(() => Component.literal("All your debug cards were invalidated."), false)
      1
    }
    else {
      source.sendFailure(Component.literal("You are not whitelisted to use debug card."))
      0
    }
  }

  private def list(source: CommandSourceStack): Int = {
    val players = whitelist.whitelist
    if (players.nonEmpty)
      source.sendSuccess(() => Component.literal("Currently whitelisted players: " + players.mkString(", ")), false)
    else
      source.sendFailure(Component.literal("There are no currently whitelisted players."))
    players.size
  }

  private def add(source: CommandSourceStack, player: String): Int = {
    whitelist.add(player)
    source.sendSuccess(() => Component.literal("Player was added to whitelist."), false)
    1
  }

  private def remove(source: CommandSourceStack, player: String): Int = {
    whitelist.remove(player)
    source.sendSuccess(() => Component.literal("Player was removed from whitelist."), false)
    1
  }
}
