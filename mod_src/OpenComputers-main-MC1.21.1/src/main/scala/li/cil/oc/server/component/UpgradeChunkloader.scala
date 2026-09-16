package li.cil.oc.server.component

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.event.ChunkloaderUpgradeHandler
import net.minecraft.world.level.ChunkPos

import scala.collection.convert.ImplicitConversionsToJava._
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.entity.Entity

class UpgradeChunkloader(val host: EnvironmentHost) extends AbstractManagedEnvironment with DeviceInfo {
  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("chunkloader").
    withConnector().
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "World stabilizer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Realizer9001-CL"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  var ticket: Option[ChunkPos] = None

  override val canUpdate = true

  override def update(): Unit = {
    super.update()
    if (host.getEnvironmentLevel.getGameTime % Settings.get.tickFrequency == 0 && ticket.isDefined) {
      if (!node.tryChangeBuffer(-Settings.get.chunkloaderCost * Settings.get.tickFrequency)) {
        host.getEnvironmentLevel match {
          case world: ServerLevel => {
            ticket.foreach(pos => ChunkloaderUpgradeHandler.releaseTicket(world, node.address, pos))
          }
        }
        ticket = None
      }
      else if (host.isInstanceOf[Entity]) // Robot move events are not fired for entities (drones)
        ChunkloaderUpgradeHandler.updateLoadedChunk(this)
    }
  }

  @Callback(doc = "function():boolean -- Gets whether the chunkloader is currently active.")
  def isActive(context: Context, args: Arguments): Array[AnyRef] = result(ticket.isDefined)

  @Callback(doc = "function(enabled:boolean):boolean -- Enables or disables the chunkloader, returns true if active changed")
  def setActive(context: Context, args: Arguments): Array[AnyRef] = result(setActive(args.checkBoolean(0), throwIfBlocked = true))

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      val restoredTicket = ChunkloaderUpgradeHandler.claimTicket(node.address, host.getEnvironmentLevel)
      if (restoredTicket.isDefined) {
        if (!isDimensionAllowed) {
          host.getEnvironmentLevel match {
            case world: ServerLevel => ChunkloaderUpgradeHandler.releaseTicket(world, node.address, restoredTicket.get)
          }
          OpenComputers.log.info(s"Releasing chunk loader ticket at (${host.xPosition()}, ${host.yPosition()}, ${host.zPosition()}) in blacklisted dimension ${host.getEnvironmentLevel().dimension}.")
        } else {
          OpenComputers.log.info(s"Reclaiming chunk loader ticket at (${host.xPosition()}, ${host.yPosition()}, ${host.zPosition()}) in dimension ${host.getEnvironmentLevel().dimension}.")
          ticket = restoredTicket
          ChunkloaderUpgradeHandler.updateLoadedChunk(this)
        }
      } else host match {
        case context: Context if context.isRunning => requestTicket()
        case _ =>
      }
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      ticket.foreach(pos => host.getEnvironmentLevel match {
        case world: ServerLevel => ChunkloaderUpgradeHandler.releaseTicket(world, node.address, pos)
      })
      ticket = None
    }
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "computer.stopped") {
      setActive(enabled = false)
    }
    else if (message.name == "computer.started") {
      setActive(enabled = true)
    }
  }

  private def setActive(enabled: Boolean, throwIfBlocked: Boolean = false) = {
    if (enabled && ticket.isEmpty) {
      requestTicket(throwIfBlocked)
      ticket.isDefined
    }
    else if (!enabled && ticket.isDefined) {
      ticket.foreach(pos => host.getEnvironmentLevel match {
        case world: ServerLevel => ChunkloaderUpgradeHandler.releaseTicket(world, node.address, pos)
      })
      ticket = None
      true
    } else {
      false
    }
  }

  private def isDimensionAllowed: Boolean = {
    // These settings are retained as legacy numeric dimension IDs for config
    // compatibility. Modern/modded dimensions have no stable numeric ID.
    val legacyId = host.getEnvironmentLevel().dimension match {
      case Level.OVERWORLD => Some(0)
      case Level.NETHER => Some(-1)
      case Level.END => Some(1)
      case _ => None
    }
    val whitelist = Settings.get.chunkloadDimensionWhitelist
    val blacklist = Settings.get.chunkloadDimensionBlacklist

    if (!whitelist.isEmpty && !legacyId.exists(whitelist.contains)) {
      false
    } else if (legacyId.exists(blacklist.contains)) {
      false
    } else {
      true
    }
  }

  private def requestTicket(throwIfBlocked: Boolean = false): Unit = {
    if (!isDimensionAllowed) {
      if (throwIfBlocked) {
        throw new Exception("this dimension is blacklisted")
      }
    } else {
      ChunkloaderUpgradeHandler.requestTicket(this)
    }
  }
}
