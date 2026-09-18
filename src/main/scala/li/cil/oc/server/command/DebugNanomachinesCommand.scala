package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import li.cil.oc.api
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.commands.{CommandSourceStack, Commands}
import net.minecraft.network.chat.Component

object DebugNanomachinesCommand {
  private val Names = Seq("oc_debugNanomachines", "oc_dn")

  def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit =
    Names.foreach(name => dispatcher.register(Commands.literal(name)
      .requires(CommandHandler.canUse(_, 2))
      .executes(context => execute(context.getSource))))

  private def execute(source: CommandSourceStack): Int = {
    val player = source.getPlayerOrException
    api.Nanomachines.installController(player) match {
      case controller: ControllerImpl =>
        controller.debug()
        source.sendSuccess(() => Component.literal("Debug configuration created, see log for mappings."), false)
      case _ =>
    }
    1
  }
}
