package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import li.cil.oc.api
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.{CommandSourceStack, Commands}
import net.minecraft.server.level.ServerPlayer

object LogNanomachinesCommand {
  private val Names = Seq("oc_nanomachines", "oc_nm")

  def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit = Names.foreach { name =>
    dispatcher.register(Commands.literal(name)
      .requires(CommandHandler.canUse(_, 2))
      .executes(context => execute(context.getSource, context.getSource.getPlayerOrException))
      .then(Commands.argument("player", EntityArgument.player())
        .executes(context => execute(context.getSource, EntityArgument.getPlayer(context, "player")))))
  }

  private def execute(source: CommandSourceStack, player: ServerPlayer): Int = {
    api.Nanomachines.installController(player) match {
      case controller: ControllerImpl => controller.print()
      case _ =>
    }
    1
  }
}
