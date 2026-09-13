package li.cil.oc.common.tileentity

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.common.Tier
import li.cil.oc.integration.Mods
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.Constants.NBT
import net.minecraft.core.Direction

// Removed in MC 1.11
class AccessPoint extends Switch with WirelessEndpoint with traits.PowerAcceptor {
  var strength = Settings.get.maxWirelessRange(Tier.Two)

  var isRepeater = true

  val componentNodes = Array.fill(6)(api.Network.newNode(this, Visibility.Network).
    withComponent("access_point").
    create())

  override def isWirelessEnabled = true

  // ----------------------------------------------------------------------- //

  @SideOnly(Dist.CLIENT)
  override protected def hasConnector(side: Direction) = true

  override protected def connector(side: Direction) = sidedNode(side) match {
    case connector: Connector => Option(connector)
    case _ => None
  }

  override def energyThroughput = Settings.get.accessPointRate

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    player.addChatMessage(Localization.Analyzer.WirelessStrength(strength))
    Array(componentNodes(side))
  }

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():number -- Get the signal strength (range) used when relaying messages.""")
  def getStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized(result(strength))

  @Callback(doc = """function(strength:number):number -- Set the signal strength (range) used when relaying messages.""")
  def setStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    strength = math.max(args.checkDouble(0), math.min(0, Settings.get.maxWirelessRange(Tier.Two)))
    result(strength)
  }

  @Callback(direct = true, doc = """function():boolean -- Get whether the access point currently acts as a repeater (resend received wireless packets wirelessly).""")
  def isRepeater(context: Context, args: Arguments): Array[AnyRef] = synchronized(result(isRepeater))

  @Callback(doc = """function(enabled:boolean):boolean -- Set whether the access point should act as a repeater.""")
  def setRepeater(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    isRepeater = args.checkBoolean(0)
    result(isRepeater)
  }

  // ----------------------------------------------------------------------- //

  override def receivePacket(packet: Packet, source: WirelessEndpoint): Unit = {
    tryEnqueuePacket(None, packet)
    if (Mods.ComputerCraft.isAvailable) {
      packet.data.headOption match {
        case Some(answerPort: java.lang.Double) => queueMessage(packet.source, packet.destination, packet.port, answerPort.toInt, packet.data.drop(1))
        case _ => queueMessage(packet.source, packet.destination, packet.port, -1, packet.data)
      }
    }
  }

  override protected def relayPacket(sourceSide: Option[Direction], packet: Packet): Unit = {
    super.relayPacket(sourceSide, packet)
    if (strength > 0 && (sourceSide.isDefined || isRepeater)) {
      val cost = Settings.get.wirelessCostPerRange(Tier.Two)
      val tryChangeBuffer = sourceSide match {
        case Some(side) =>
          (amount: Double) => plugs(side.ordinal).node.asInstanceOf[Connector].tryChangeBuffer(amount)
        case _ =>
          (amount: Double) => plugs.exists(_.node.asInstanceOf[Connector].tryChangeBuffer(amount))
      }
      if (tryChangeBuffer(-strength * cost)) {
        api.Network.sendWirelessPacket(this, strength, packet)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def createNode(plug: Plug) = api.Network.newNode(plug, Visibility.Network).
    withConnector(math.round(Settings.get.bufferAccessPoint)).
    create()

  override protected def onPlugConnect(plug: Plug, node: Node): Unit = {
    super.onPlugConnect(plug, node)
    if (node == plug.node) {
      api.Network.joinWirelessNetwork(this)
    }
    if (plug.isPrimary)
      plug.node.connect(componentNodes(plug.side.ordinal()))
    else
      componentNodes(plug.side.ordinal).remove()
  }

  override protected def onPlugDisconnect(plug: Plug, node: Node): Unit = {
    super.onPlugDisconnect(plug, node)
    if (node == plug.node) {
      api.Network.leaveWirelessNetwork(this)
    }
    if (plug.isPrimary && node != plug.node)
      plug.node.connect(componentNodes(plug.side.ordinal()))
    else
      componentNodes(plug.side.ordinal).remove()
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag) = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "strength")) {
      strength = nbt.getDouble(Settings.namespace + "strength") max 0 min Settings.get.maxWirelessRange(Tier.Two)
    }
    if (nbt.contains(Settings.namespace + "isRepeater")) {
      isRepeater = nbt.getBoolean(Settings.namespace + "isRepeater")
    }
    nbt.getList(Settings.namespace + "componentNodes", NBT.TAG_COMPOUND).toArray[CompoundTag].
      zipWithIndex.foreach {
      case (tag, index) => componentNodes(index).load(tag)
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag) = {
    super.writeToNBTForServer(nbt)
    nbt.putDouble(Settings.namespace + "strength", strength)
    nbt.putBoolean(Settings.namespace + "isRepeater", isRepeater)
    nbt.setNewTagList(Settings.namespace + "componentNodes", componentNodes.map {
      case node: Node =>
        val tag = new CompoundTag()
        node.save(tag)
        tag
      case _ => new CompoundTag()
    })
  }
}
