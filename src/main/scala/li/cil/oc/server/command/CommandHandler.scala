package li.cil.oc.server.command

import cpw.mods.fml.common.event.FMLServerStartingEvent

object CommandHandler {
  def register(e: FMLServerStartingEvent): Unit = {
    e.registerServerCommand(DebugNanomachinesCommand)
    e.registerServerCommand(LogNanomachinesCommand)
    e.registerServerCommand(NetworkProfilingCommand)
    e.registerServerCommand(NonDisassemblyAgreementCommand)
    e.registerServerCommand(WirelessRenderingCommand)
    e.registerServerCommand(SpawnComputerCommand)
    e.registerServerCommand(DebugWhitelistCommand)
    e.registerServerCommand(SendDebugMessageCommand)
  }
}
