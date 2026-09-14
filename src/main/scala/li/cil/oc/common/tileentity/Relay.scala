package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.common.{InventorySlots, Slot, Tier}
import li.cil.oc.common.item.Delegator
import li.cil.oc.util.ItemStackNBTExtensions._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import scala.collection.mutable

/**
 * 中继器 / 接入点（原 1.7.10 `common.tileentity.Relay`）：把有线网络与无线网络、
 * 「已配对卡」建立的私有隧道互相桥接，并可开启 / 关闭中继转发。
 *
 * 纹理：下/上 = RelayTop，北 = RelayFront，其它 = RelaySide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `getSizeInventory` → `getSlots`、`getInventoryStackLimit` → `getSlotLimit`、
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `player.addChatMessage(...)` → `player.displayClientMessage(..., false)`。
 *  - `ForgeDirection` → `Direction`（无 `UNKNOWN`，`sourceSide` 用 `Option[Direction]`）。
 *  - `nbt.hasKey/getDouble/setDouble` → `contains/getDouble/putDouble`；
 *    `NBT.TAG_COMPOUND` → [[net.minecraft.nbt.Tag.TAG_COMPOUND]]；
 *    `ListTag#toArray[T]` 会被 Java 的 `AbstractCollection#toArray` 抢走，改用
 *    [[li.cil.oc.util.ExtendedNBT]] 的 `map`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）。
 *
 * 降级清单（详见各处的 TODO）：
 *  - `li.cil.oc.server.network.QuantumNetwork` 未移植 → 见 [[Relay.QuantumNetwork]]，
 *    本地实现同名的隧道注册表与 `QuantumNode`（`tunnel` / `receivePacket`）语义。
 *  - `li.cil.oc.integration.opencomputers.DriverLinkedCard.dataTag` 未移植 →
 *    见 [[linkedCardDataTag]]（内联 `integration.opencomputers.Item.dataTag` 的等价逻辑）。
 *  - `li.cil.oc.integration.Mods.ComputerCraft`（`queueMessage` 转发事件到 ComputerCraft）
 *    不再移植 → 见 [[tryEnqueuePacket]]。
 */
class Relay(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.SwitchLike with traits.ComponentInventory with traits.PowerAcceptor
    with Analyzable with WirelessEndpoint with Relay.QuantumNetwork.QuantumNode {

  lazy final val WirelessNetworkCardTier1 = api.Items.get(Constants.ItemName.WirelessNetworkCardTier1)
  lazy final val WirelessNetworkCardTier2 = api.Items.get(Constants.ItemName.WirelessNetworkCardTier2)
  lazy final val LinkedCard = api.Items.get(Constants.ItemName.LinkedCard)

  var wirelessTier = -1

  override def isWirelessEnabled: Boolean = wirelessTier >= Tier.One

  def maxWirelessRange: Double = if (wirelessTier == Tier.One || wirelessTier == Tier.Two)
    Settings.get.maxWirelessRange(wirelessTier) else 0

  def wirelessCostPerRange: Double = if (wirelessTier == Tier.One || wirelessTier == Tier.Two)
    Settings.get.wirelessCostPerRange(wirelessTier) else 0

  var strength = maxWirelessRange

  var isRepeater = true

  var isLinkedEnabled = false

  override var tunnel: String = "creative"

  val componentNodes = Array.fill(6)(api.Network.newNode(this, Visibility.Network).
    withComponent("relay").
    create())

  override def canUpdate: Boolean = isServer

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（只应由客户端渲染调用）。
  override protected def hasConnector(side: Direction): Boolean = true

  override protected def connector(side: Direction): Option[Connector] = sidedNode(side) match {
    case connector: Connector => Option(connector)
    case _ => None
  }

  override def energyThroughput: Double = Settings.get.accessPointRate

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    if (isWirelessEnabled) {
      // 原 `player.addChatMessage(...)`；1.21.1 改用 `displayClientMessage`。
      player.displayClientMessage(Localization.Analyzer.WirelessStrength(strength), false)
      Array(componentNodes(side))
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

  override def tryEnqueuePacket(sourceSide: Option[Direction], packet: Packet): Boolean = {
    // TODO(integration.ComputerCraft): 原实现先判断 `Mods.ComputerCraft.isAvailable`，
    // 若可用则用 `queueMessage(...)` 把网络包转发成 ComputerCraft 的 `modem_message` 事件
    // （依赖 `dan200.computercraft.api.peripheral.IComputerAccess`）。`integration` 包与
    // ComputerCraft 本身都不再移植，因此这里直接交给父类入队。
    super.tryEnqueuePacket(sourceSide, packet)
  }

  override protected def relayPacket(sourceSide: Option[Direction], packet: Packet): Unit = {
    super.relayPacket(sourceSide, packet)

    val tryChangeBuffer: Double => Boolean = sourceSide match {
      case Some(side) =>
        (amount: Double) => plugs(side.ordinal()).node.asInstanceOf[Connector].tryChangeBuffer(amount)
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
        val endpoints = Relay.QuantumNetwork.getEndpoints(tunnel).filter(_ != this)
        for (endpoint <- endpoints) {
          endpoint.receivePacket(packet)
        }
      }
    }

    onSwitchActivity()
  }

  // ----------------------------------------------------------------------- //

  override protected def createNode(plug: Plug): Node = api.Network.newNode(plug, Visibility.Network).
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
        relayAmount = math.max(1, relayBaseAmount + (Delegator.subItem(stack) match {
          case Some(ram: li.cil.oc.common.item.Memory) => (ram.tier + 1) * relayAmountPerUpgrade
          case _ => (driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2)
        }))
      case Some(driver) if driver.slot(stack) == Slot.HDD =>
        maxQueueSize = math.max(1, queueBaseSize + (driver.tier(stack) + 1) * queueSizePerUpgrade)
      case Some(driver) if driver.slot(stack) == Slot.Card =>
        val descriptor = api.Items.get(stack)
        if (descriptor == WirelessNetworkCardTier1 || descriptor == WirelessNetworkCardTier2)
          wirelessTier = if (descriptor == WirelessNetworkCardTier1) Tier.One else Tier.Two
        if (descriptor == LinkedCard) {
          val data = linkedCardDataTag(stack)
          if (data.contains(Settings.namespace + "tunnel")) {
            tunnel = data.getString(Settings.namespace + "tunnel")
            isLinkedEnabled = true
            Relay.QuantumNetwork.add(this)
          }
        }
      case _ => // Dafuq u doin.
    }
  }

  /**
   * 取「已配对卡」物品上的数据标签（隧道名存在 `oc:data.oc:tunnel` 下）。
   *
   * TODO(integration.opencomputers.DriverLinkedCard): 原实现为
   * `DriverLinkedCard.dataTag(stack)`，它继承自 `integration.opencomputers.Item.dataTag`。
   * `integration` 包未纳入编译范围，这里内联同一逻辑；
   * 等该包移植后改回调用 [[li.cil.oc.integration.opencomputers.DriverLinkedCard]]。
   */
  private def linkedCardDataTag(stack: ItemStack): CompoundTag = {
    if (stack == null || stack.isEmpty || !stack.hasTag()) return new CompoundTag()
    val nbt = stack.getTag()
    val key = Settings.namespace + "data"
    if (!nbt.contains(key)) return new CompoundTag()
    nbt.getCompound(key)
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
        Relay.QuantumNetwork.remove(this)
      case _ =>
    }
  }

  override def getSlots: Int = InventorySlots.relay.length

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    slot >= 0 && slot < InventorySlots.relay.length &&
      Option(Driver.driverFor(stack, getClass)).fold(false)(driver => {
        val provided = InventorySlots.relay(slot)
        val tierSatisfied = driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
        val cardTypeSatisfied = if (provided.slot == Slot.Card) api.Items.get(stack) == WirelessNetworkCardTier1 ||
          api.Items.get(stack) == WirelessNetworkCardTier2 || api.Items.get(stack) == LinkedCard else true
        tierSatisfied && cardTypeSatisfied
      })

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    for (slot <- items.indices) items(slot) collect {
      case stack => updateLimits(slot, stack)
    }

    if (nbt.contains(Settings.namespace + "strength")) {
      strength = nbt.getDouble(Settings.namespace + "strength") max 0 min maxWirelessRange
    }
    if (nbt.contains(Settings.namespace + "isRepeater")) {
      isRepeater = nbt.getBoolean(Settings.namespace + "isRepeater")
    }
    nbt.getList(Settings.namespace + "componentNodes", Tag.TAG_COMPOUND).
      map((tag: CompoundTag) => tag).
      zipWithIndex.foreach {
      case (tag, index) => if (index < componentNodes.length) componentNodes(index).load(tag)
    }
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putDouble(Settings.namespace + "strength", strength)
    nbt.putBoolean(Settings.namespace + "isRepeater", isRepeater)
    nbt.setNewTagList(Settings.namespace + "componentNodes", componentNodes.toIndexedSeq.map {
      case node: Node =>
        val tag = new CompoundTag()
        node.save(tag)
        tag
      case _ => new CompoundTag()
    })
  }
}

/**
 * `Relay` 的伴生对象。
 *
 * TODO(server.network.QuantumNetwork): 1.7.10 的 `li.cil.oc.server.network.QuantumNetwork`
 * 是一个「隧道名 → 参与节点集合」的**弱引用**注册表（`add` / `remove` / `getEndpoints`），
 * 用于让插了「已配对卡」的中继器之间跨维度互相转发网络包。服务端网络层尚未移植，
 * 这里内联一份语义等价的实现（见 [[Relay.QuantumNetwork]]）；
 * 网络层移植后把这里整体删除，改回 `import li.cil.oc.server.network.QuantumNetwork`
 * 并让 `Relay` 混入 `QuantumNetwork.QuantumNode`（调用点无需改动）。
 */
object Relay {

  object QuantumNetwork {
    /** `tunnel` 名 → 参与该隧道的量子节点（弱引用，避免阻塞区块卸载）。 */
    val tunnels = mutable.Map.empty[String, mutable.WeakHashMap[QuantumNode, Unit]]

    def add(card: QuantumNode): Unit =
      tunnels.getOrElseUpdate(card.tunnel, mutable.WeakHashMap.empty).put(card, ())

    def remove(card: QuantumNode): Unit =
      tunnels.get(card.tunnel).foreach(_.remove(card))

    def getEndpoints(tunnel: String): Iterable[QuantumNode] =
      tunnels.get(tunnel).fold(Iterable.empty[QuantumNode])(_.keys)

    /** 与 1.7.10 同名 trait：成员名保持不变（`tunnel` / `receivePacket`）。 */
    trait QuantumNode {
      def tunnel: String

      def receivePacket(packet: Packet): Unit
    }
  }

}
