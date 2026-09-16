package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import li.cil.oc.Settings
import net.minecraft.commands.{CommandSourceStack, Commands}

object WirelessRenderingCommand {
  private val Names = Seq("oc_renderWirelessNetwork", "oc_wlan")

  def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit = Names.foreach { name =>
    dispatcher.register(Commands.literal(name)
      .requires(CommandHandler.canUse(_, 2))
      .executes(_ => execute(None))
      .then(Commands.argument("value", BoolArgumentType.bool())
        .executes(context => execute(Some(BoolArgumentType.getBool(context, "value"))))))
  }

  private def execute(requestedValue: Option[Boolean]): Int = {
    Settings.rTreeDebugRenderer = requestedValue.getOrElse(!Settings.rTreeDebugRenderer)
    1
  }
}
