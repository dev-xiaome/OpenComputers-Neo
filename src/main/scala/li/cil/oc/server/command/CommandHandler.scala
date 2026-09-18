package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.neoforged.neoforge.event.RegisterCommandsEvent

object CommandHandler {
  def canUse(source: CommandSourceStack, permissionLevel: Int): Boolean =
    source.hasPermission(permissionLevel) || source.getServer.isSingleplayer

  def onRegisterCommands(event: RegisterCommandsEvent): Unit = register(event.getDispatcher)

  private def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit = {
    DebugNanomachinesCommand.register(dispatcher)
    LogNanomachinesCommand.register(dispatcher)
    NonDisassemblyAgreementCommand.register(dispatcher)
    WirelessRenderingCommand.register(dispatcher)
    SpawnComputerCommand.register(dispatcher)
    DebugWhitelistCommand.register(dispatcher)
    SendDebugMessageCommand.register(dispatcher)
  }
}
