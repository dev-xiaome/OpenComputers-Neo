package li.cil.oc.common.blockentity

import com.google.common.base.Charsets
import dan200.computercraft.api.peripheral.IComputerAccess
import li.cil.oc.api.Driver
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.common._
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.integration.Mods
import li.cil.oc.server.PacketSender
import li.cil.oc.server.network.QuantumNetwork
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.{Constants, Localization, Settings, api}
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.nbt.{CompoundTag, ListTag, Tag}
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import scala.collection.mutable

class Relay(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.RELAY.get(), pos, state) with traits.Hub with traits.ComponentInventory
  with traits.PowerAcceptor with Analyzable with WirelessEndpoint with QuantumNetwork.QuantumNode with MenuProvider
    with IBlockEntityExtension {

  lazy final val WirelessNetworkCardTier1: ItemInfo = api.Items.get(Constants.ItemName.WirelessNetworkCardTier1)
  lazy final val WirelessNetworkCardTier2: ItemInfo = api.Items.get(Constants.ItemName.WirelessNetworkCardTier2)
  lazy final val LinkedCard: ItemInfo = api.Items.get(Constants.ItemName.LinkedCard)

  override def getWirelessLevel: Level = level
  
  var strength: Double = maxWirelessRange

  var isRepeater = true

  var wirelessTier = -1
  
  def isWirelessEnabled = wirelessTier >= Tier.One

  def maxWirelessRange = if (wirelessTier == Tier.One || wirelessTier == Tier.Two)
    Settings.get.maxWirelessRange(wirelessTier) else 0

  def wirelessCostPerRange = if (wirelessTier == Tier.One || wirelessTier == Tier.Two)
    Settings.get.wirelessCostPerRange(wirelessTier) else 0
  
  var isLinkedEnabled = false

  var tunnel = "creative"

  val componentNodes: Array[Component] = Array.fill(6)(api.Network.newNode(this, Visibility.Network).
    withComponent("relay").
    create())

  val openPorts = mutable.Map.empty[AnyRef, mutable.Set[Int]]

  var lastMessage = 0L

  def onSwitchActivity(): Unit = {
    val now = System.currentTimeMillis()
    if (now - lastMessage >= (relayDelay - 1) * 50) {
      lastMessage = now
      PacketSender.sendSwitchActivity(this)
    }
  }

  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  override protected def hasConnector(side: Direction) = true

  override protected def connector(side: Direction): Option[Connector] = sidedNode(side) match {
    case connector: Connector => Option(connector)
    case _ => None
  }

  override def energyThroughput: Double = Settings.get.accessPointRate

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    if (isWirelessEnabled) {
      player.sendSystemMessage(Localization.Analyzer.WirelessStrength(strength))
      Array(componentNodes(side.get3DDataValue))
    }
    else null
  }

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():number -- Get the signal strength (range) used when relaying messages.""")
  def getStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized(result(strength))

  @Callback(doc = """function(strength:number):number -- Set the signal strength (range) used when relaying messages.""")
  def setStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    strength = math.max(0, math.min(args.checkDouble(0), maxWirelessRange))
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

// Isolated from parent class so automatic callbacks don't depend on optional mods.
  protected object RelayCCAdapter {
    def queueMessage(source: String, destination: String, port: Int, answerPort: Int, args: Array[AnyRef]): Unit = {
      computers.foreach { c =>
        val computer: IComputerAccess = c.asInstanceOf[IComputerAccess]
        val address = s"cc${computer.getID}_${computer.getAttachmentName}"
        if (source != address && Option(destination).forall(_ == address) && openPorts(computer).contains(port)) {
          val header = Seq(computer.getAttachmentName, Int.box(port), Int.box(answerPort))
          val payload = args.map {
            case x: Array[Byte] => new String(x, Charsets.UTF_8)
            case x => x
          }
          computer.queueEvent("modem_message", Array((header :+ (if (payload.length > 1) payload else payload(0))): _*): _*)
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def receivePacket(packet: Packet, source: WirelessEndpoint): Unit = {
    if (isWirelessEnabled) {
      tryEnqueuePacket(None, packet)
    }
  }

  override def receivePacket(packet: Packet): Unit = {
    if (isLinkedEnabled) {
      tryEnqueuePacket(None, packet)
    }
  }

  val computers = mutable.Buffer.empty[AnyRef]

  override def tryEnqueuePacket(sourceSide: Option[Direction], packet: Packet): Boolean = {
    if (Mods.ComputerCraft.isModAvailable) {
      packet.data.headOption match {
        case Some(answerPort: java.lang.Double) => RelayCCAdapter.queueMessage(packet.source, packet.destination, packet.port, answerPort.toInt, packet.data.drop(1))
        case _ => RelayCCAdapter.queueMessage(packet.source, packet.destination, packet.port, -1, packet.data)
      }
    }
    super.tryEnqueuePacket(sourceSide, packet)
  }

  override protected def relayPacket(sourceSide: Option[Direction], packet: Packet): Unit = {
    super.relayPacket(sourceSide, packet)

    val tryChangeBuffer = sourceSide match {
      case Some(side) =>
        (amount: Double) => plugs(side.ordinal).node.asInstanceOf[Connector].tryChangeBuffer(amount)
      case _ =>
        (amount: Double) => plugs.exists(_.node.asInstanceOf[Connector].tryChangeBuffer(amount))
    }

    if (isWirelessEnabled && strength > 0 && (sourceSide.isDefined || isRepeater)) {
      val cost = wirelessCostPerRange
      if (tryChangeBuffer(-strength * cost)) {
        api.Network.sendWirelessPacket(this, strength, packet)
      }
    }

    if (isLinkedEnabled && sourceSide.isDefined) {
      val cost = packet.size / 32.0 + wirelessCostPerRange * maxWirelessRange * 5
      if (tryChangeBuffer(-cost)) {
        val endpoints = QuantumNetwork.getEndpoints(tunnel).filter(_ != this)
        for (endpoint <- endpoints) {
          endpoint.receivePacket(packet)
        }
      }
    }

    onSwitchActivity()
  }

  // ----------------------------------------------------------------------- //

  override protected def createNode(plug: Plug): Connector = api.Network.newNode(plug, Visibility.Network).
    withConnector(math.round(Settings.get.bufferAccessPoint).toDouble).
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

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    updateLimits(slot, stack)
  }
  
  private def updateLimits(slot: Int, stack: ItemStack): Unit = {
    Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver) if driver.slot(stack) == Slot.CPU =>
        relayDelay = math.max(1, relayBaseDelay - ((driver.tier(stack) + 1) * relayDelayPerUpgrade).toInt)
      case Some(driver) if driver.slot(stack) == Slot.Memory =>
        relayAmount = math.max(1, relayBaseAmount + (stack.getItem match {
          case ram: item.Memory => (ram.tier + 1) * relayAmountPerUpgrade
          case _ => (driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2)
        }))
      case Some(driver) if driver.slot(stack) == Slot.HDD =>
        maxQueueSize = math.max(1, queueBaseSize + (driver.tier(stack) + 1) * queueSizePerUpgrade)
      case Some(driver) if driver.slot(stack) == Slot.Card =>
        val descriptor = api.Items.get(stack)
        if (descriptor == WirelessNetworkCardTier1 || descriptor == WirelessNetworkCardTier2)
          wirelessTier = if (descriptor == WirelessNetworkCardTier1) Tier.One else Tier.Two
        if (descriptor == LinkedCard) {
          for(tunnelTag <- stack.getComponent(OCComponents.TUNNEL)) {
            tunnel = tunnelTag
            isLinkedEnabled = true
            QuantumNetwork.add(this)
          }
        }
      case _ => // Dafuq u doin.
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    Driver.driverFor(stack, getClass) match {
      case driver if driver.slot(stack) == Slot.CPU => relayDelay = relayBaseDelay
      case driver if driver.slot(stack) == Slot.Memory => relayAmount = relayBaseAmount
      case driver if driver.slot(stack) == Slot.HDD => maxQueueSize = queueBaseSize
      case driver if driver.slot(stack) == Slot.Card =>
        wirelessTier = -1
        isLinkedEnabled = false
        QuantumNetwork.remove(this)
    }
  }

  override def getContainerSize: Int = InventorySlots.relay.length

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean =
    Option(Driver.driverFor(stack, getClass)).fold(false)(driver => {
      val provided = InventorySlots.relay(slot)
      val tierSatisfied = driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
      val cardTypeSatisfied = if (provided.slot == Slot.Card) api.Items.get(stack) == WirelessNetworkCardTier1 ||
        api.Items.get(stack) == WirelessNetworkCardTier2 || api.Items.get(stack) == LinkedCard else true
      tierSatisfied && cardTypeSatisfied
    })

  // ----------------------------------------------------------------------- //

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new menu.Relay(id, playerInventory, this)

  // ----------------------------------------------------------------------- //

  private final val StrengthTag = Settings.namespace + "strength"
  private final val IsRepeaterTag = Settings.namespace + "isRepeater"
  private final val ComponentNodesTag = Settings.namespace + "componentNodes"

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    for (slot <- items.indices) if (!items(slot).isEmpty) {
      updateLimits(slot, items(slot))
    }

    if (nbt.contains(StrengthTag)) {
      strength = nbt.getDouble(StrengthTag) max 0 min maxWirelessRange
    }
    if (nbt.contains(IsRepeaterTag)) {
      isRepeater = nbt.getBoolean(IsRepeaterTag)
    }
    val list = nbt.getList(ComponentNodesTag, Tag.TAG_COMPOUND)
    for (i <- 0 until math.min(list.size(), componentNodes.length)) {
      val tag = list.getCompound(i)
      componentNodes(i).loadData(tag, provider)
    }
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForServer(nbt, provider)
    nbt.putDouble(StrengthTag, strength)
    nbt.putBoolean(IsRepeaterTag, isRepeater)
    val componentNodesList = new ListTag()
    componentNodes.foreach {
      case node: Node =>
        val tag = new CompoundTag()
        node.saveData(tag, provider)
        componentNodesList.add(tag)
      case _ => 
        componentNodesList.add(new CompoundTag())
    }
    nbt.put(ComponentNodesTag, componentNodesList)
  }
}
