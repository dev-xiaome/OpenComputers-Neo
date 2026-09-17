package li.cil.oc.common.blockentity

import com.google.common.base.Charsets
import li.cil.oc.{Constants, Localization, Settings, api}
import li.cil.oc.api.Driver
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.common._
import li.cil.oc.integration.Mods
import li.cil.oc.integration.opencomputers.DriverLinkedCard
import li.cil.oc.server.PacketSender
import li.cil.oc.server.network.QuantumNetwork
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, ListTag, Tag}
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.{Dist, OnlyIn}

import scala.collection.mutable

class Relay(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.RELAY.get(), pos, state) with traits.Hub with traits.ComponentInventory
  with traits.PowerAcceptor with Analyzable with WirelessEndpoint with QuantumNetwork.QuantumNode with MenuProvider {

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

  // 1.21.1 移除：原 `RelayCCAdapter`（把消息桥接到 ComputerCraft 计算机）。
  // 它直接依赖 `dan200.computercraft.api.peripheral.IComputerAccess`，而整个
  // `li.cil.oc.integration.computercraft` 包已被隔离到 `src/main/scala-pending`（不在编译集里），
  // 因此这里不能再有编译期引用。将来恢复 CC 集成时，请把这段桥接逻辑一并挪进
  // `integration/computercraft/RelayPeripheral.scala`（那里本来就要拿到 `IComputerAccess`）。
  // `computers` / `openPorts` 两个容器保留，供恢复集成时复用。

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
    // 1.21.1：原先这里会在 CC 可用时把包转发给 `RelayCCAdapter`；
    // ComputerCraft 集成已隔离到 `src/main/scala-pending`，故该分支移除（见上）。
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
          val data = DriverLinkedCard.dataTag(stack)
          if (data.contains(Settings.namespace + "tunnel")) {
            tunnel = data.getString(Settings.namespace + "tunnel")
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

  override def loadForServer(nbt: CompoundTag): Unit = {
    super.loadForServer(nbt)
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
      componentNodes(i).loadData(tag)
    }
  }

  override def saveForServer(nbt: CompoundTag): Unit = {
    super.saveForServer(nbt)
    nbt.putDouble(StrengthTag, strength)
    nbt.putBoolean(IsRepeaterTag, isRepeater)
    val componentNodesList = new ListTag()
    componentNodes.foreach {
      case node: Node =>
        val tag = new CompoundTag()
        node.saveData(tag)
        componentNodesList.add(tag)
      case _ => 
        componentNodesList.add(new CompoundTag())
    }
    nbt.put(ComponentNodesTag, componentNodesList)
  }
}
