package li.cil.oc.server

import java.util.logging.Level

import org.apache.logging.log4j.LogManager
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.server.management.UserListOpsEntry
import net.minecraft.util.{ChatComponentText, Component}

import scala.language.implicitConversions

package object command {
  implicit def string2text(s: String): Component = new ChatComponentText(s)

  def getOpLevel(sender: ICommandSender): Int = {
    // Shitty minecraft server logic & shitty minecraft server code.
    val srv = MinecraftServer.getServer
    if (srv.isSinglePlayer && srv.worldServers.head.getWorldInfo.areCommandsAllowed &&
      srv.getServerOwner.equalsIgnoreCase(sender.getCommandSenderName) /* || srv.commandsAllowedForAll */ )
      return 4

    sender match {
      case _: MinecraftServer => 4
      case p: ServerPlayer =>
        val e = srv.getConfigurationManager.func_152603_m.func_152683_b(p.getGameProfile)
        if (e == null) 0 else e.asInstanceOf[UserListOpsEntry].func_152644_a()
      case _ => 0
    }
  }
}
