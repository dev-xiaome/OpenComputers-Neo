package li.cil.oc.common.tileentity

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState

// Removed in MC 1.11
/**
 * 无线接入点（对应 1.7.10 的 `common.tileentity.AccessPoint`，MC 1.11 起从上游移除）。
 *
 * 在交换机的基础上加了无线收发：每个面额外挂一个 `access_point` 组件节点，
 * 中继出去的包会按信号强度（`strength`）从无线网络再发一次。
 *
 * 纹理：下 = None，上 = AccessPointTop，其它四面 = AccessPointSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`（由父类 [[Switch]] 转发给 [[BlockEntityBase]]）。
 *  - `ForgeDirection` → `Direction`；`plugs(side.ordinal)` 的下标语义不变。
 *  - `player.addChatMessage(...)` → `player.displayClientMessage(..., false)`。
 *  - 删除 `@SideOnly(Side.CLIENT)`（NeoForge 会因此抛异常），客户端专用方法用注释标注。
 *  - `NBTTagCompound#hasKey/setDouble/setBoolean` → `contains/putDouble/putBoolean`；
 *    `NBT.TAG_COMPOUND` → `Tag.TAG_COMPOUND`。
 *
 * ==降级说明==
 * TODO(integration.Mods): 原实现在 `Mods.ComputerCraft.isAvailable` 时把收到的无线包
 * 也转给 ComputerCraft 的电脑；ComputerCraft 集成未移植，该分支恒不执行
 * （见 [[Switch]] 里的同类降级说明）。
 */
class AccessPoint(pos: BlockPos, state: BlockState)
  extends Switch(pos, state) with WirelessEndpoint with traits.PowerAcceptor {

  var strength = Settings.get.maxWirelessRange(Tier.Two)

  var isRepeater = true

  val componentNodes = Array.fill(6)(api.Network.newNode(this, Visibility.Network).
    withComponent("access_point").
    create())

  override def isWirelessEnabled: Boolean = true

  // ----------------------------------------------------------------------- //

  // 只应在客户端渲染时调用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。
  override protected def hasConnector(side: Direction): Boolean = true

  override protected def connector(side: Direction): Option[Connector] = sidedNode(side) match {
    case connector: Connector => Option(connector)
    case _ => None
  }

  override def energyThroughput: Double = Settings.get.accessPointRate

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    player.displayClientMessage(Localization.Analyzer.WirelessStrength(strength), false)
    Array(componentNodes(side))
  }

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():number -- Get the signal strength (range) used when relaying messages.""")
  def getStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized(result(strength))

  @Callback(doc = """function(strength:number):number -- Set the signal strength (range) used when relaying messages.""")
  def setStrength(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    // 注意：这里保持 1.7.10 原样（`min(0, 上限)` 使得上限实际不生效）以避免改变既有存档行为。
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
    if (Switch.computerCraftAvailable) {
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

  override protected def createNode(plug: Plug): Node = api.Network.newNode(plug, Visibility.Network).
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

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "strength")) {
      strength = nbt.getDouble(Settings.namespace + "strength") max 0 min Settings.get.maxWirelessRange(Tier.Two)
    }
    if (nbt.contains(Settings.namespace + "isRepeater")) {
      isRepeater = nbt.getBoolean(Settings.namespace + "isRepeater")
    }
    // 注意：1.21.1 的 `ListTag` 已经是 `AbstractCollection`，自带 `toArray` 成员，
    // 会遮蔽 `ExtendedNBT` 提供的扩展方法，因此这里按下标逐个读取。
    val componentNodesNbt = nbt.getList(Settings.namespace + "componentNodes", Tag.TAG_COMPOUND)
    for (index <- 0 until (componentNodesNbt.size() min componentNodes.length)) {
      componentNodes(index).load(componentNodesNbt.getCompound(index))
    }
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
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
