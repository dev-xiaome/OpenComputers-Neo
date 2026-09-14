package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network._
import li.cil.oc.common.tileentity.traits
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.MovingAverage
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

import scala.collection.mutable

/**
 * 组件总线（交换机 / 集线器）方块实体 trait（对应 1.7.10 的 `traits.Hub`）。
 *
 * 每个面持有一个 [[api.network.Environment]]（"plug"）节点：两侧网络通过本 trait 中继
 * 网络包（带 TTL、队列上限与中继冷却），中继统计用 [[MovingAverage]] 平滑。
 *
 * ==1.21.1 迁移要点==
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`（顺序与原 `ForgeDirection`
 *    的 ordinal 一致，`plugs(side.ordinal)` 的索引语义不变）。
 *  - `Direction` 没有 `UNKNOWN`：所有 `side` 参数都必然是真实方向，`canConnect` 只做空值判断。
 *  - `world.getTotalWorldTime` → `world.getGameTime`。
 *  - `NBTTagCompound#hasKey/getInteger/setInteger` → `contains/getInt/putInt`；
 *    `tag.getDirection` / `tag.setDirection` 由 [[li.cil.oc.util.ExtendedNBT]] 提供
 *    （方向以 byte 存取，`-1` 表示「无来源侧」）。
 *  - 删除 `@SideOnly(Side.CLIENT)`（NeoForge 会因此抛异常）；`canConnect` 本来就只在客户端调用。
 */
trait Hub extends traits.Environment with SidedEnvironment {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  override def node: Node = null

  override protected def isConnected = plugs.exists(plug =>
    plug != null &&
      plug.node != null &&
      plug.node.address != null &&
      plug.node.network != null)

  protected val plugs = Direction.values().map(side => createPlug(side))

  val queue = mutable.Queue.empty[(Option[Direction], Packet)]

  var maxQueueSize = queueBaseSize

  var relayDelay = relayBaseDelay

  var relayAmount = relayBaseAmount

  var relayCooldown = -1

  // 20 cycles
  val packetsPerCycleAvg = new MovingAverage(20)

  // ----------------------------------------------------------------------- //

  protected def queueBaseSize = Settings.get.switchDefaultMaxQueueSize

  protected def queueSizePerUpgrade = Settings.get.switchQueueSizeUpgrade

  protected def relayBaseDelay = Settings.get.switchDefaultRelayDelay

  protected def relayDelayPerUpgrade = Settings.get.switchRelayDelayUpgrade

  protected def relayBaseAmount = Settings.get.switchDefaultRelayAmount

  protected def relayAmountPerUpgrade = Settings.get.switchRelayAmountUpgrade

  // ----------------------------------------------------------------------- //

  // 仅客户端调用；1.7.10 的 `@SideOnly(Side.CLIENT)` 已删除（NeoForge 会因此抛异常）。
  // `Direction` 没有 `UNKNOWN`，参数必然是真实方向。
  override def canConnect(side: Direction): Boolean = side != null

  override def sidedNode(side: Direction): Node = if (side != null) plugs(side.ordinal()).node else null

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    super.tick()
    if (relayCooldown > 0) {
      relayCooldown -= 1
    }
    else {
      relayCooldown = -1
      if (queue.nonEmpty) queue.synchronized {
        val packetsToRely = math.min(queue.size, relayAmount)
        packetsPerCycleAvg += packetsToRely
        for (i <- 0 until packetsToRely) {
          val (sourceSide, packet) = queue.dequeue()
          relayPacket(sourceSide, packet)
        }
        if (queue.nonEmpty) {
          relayCooldown = relayDelay - 1
        }
      }
      else if (world != null && world.getGameTime % relayDelay == 0) {
        packetsPerCycleAvg += 0
      }
    }
  }

  def tryEnqueuePacket(sourceSide: Option[Direction], packet: Packet): Boolean = queue.synchronized {
    if (packet.ttl > 0 && queue.size < maxQueueSize) {
      queue += sourceSide -> packet.hop()
      if (relayCooldown < 0) {
        relayCooldown = relayDelay - 1
      }
      true
    }
    else false
  }

  protected def relayPacket(sourceSide: Option[Direction], packet: Packet): Unit = {
    for (side <- Direction.values()) {
      if (sourceSide.isEmpty || sourceSide.get != side) {
        val node = sidedNode(side)
        if (node != null) {
          node.sendToReachable("network.message", packet)
        }
      }
    }
  }

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    // 注意：`ListTag` 是 Java 集合，它自带的 `toArray` 会遮蔽 `ExtendedListTag` 的隐式扩展
    //（`toArray[T: ClassTag]`），因此这里改用扩展提供的 `map` 把元素逐个取出来。
    nbt.getList(Settings.namespace + "plugs", Tag.TAG_COMPOUND).
      map((tag: CompoundTag) => tag).
      zipWithIndex.foreach {
      case (tag, index) => plugs(index).node.load(tag)
    }
    nbt.getList(Settings.namespace + "queue", Tag.TAG_COMPOUND).foreach(
      (tag: CompoundTag) => {
        val side = tag.getDirection("side")
        val packet = api.Network.newPacket(tag)
        queue += side -> packet
      })
    if (nbt.contains(Settings.namespace + "relayCooldown")) {
      relayCooldown = nbt.getInt(Settings.namespace + "relayCooldown")
    }
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = queue.synchronized {
    super.writeToNBTForServer(nbt)
    // Side check for Waila (and other mods that may call this client side).
    if (isServer) {
      nbt.setNewTagList(Settings.namespace + "plugs", plugs.map(plug => {
        val plugNbt = new CompoundTag()
        if (plug.node != null)
          plug.node.save(plugNbt)
        plugNbt
      }).toIndexedSeq)
      nbt.setNewTagList(Settings.namespace + "queue", queue.map {
        case (sourceSide, packet) =>
          val tag = new CompoundTag()
          tag.setDirection("side", sourceSide)
          packet.save(tag)
          tag
      }.toIndexedSeq)
      if (relayCooldown > 0) {
        nbt.putInt(Settings.namespace + "relayCooldown", relayCooldown)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  protected def createPlug(side: Direction) = new Plug(side)

  protected class Plug(val side: Direction) extends api.network.Environment {
    val node = createNode(this)

    override def onMessage(message: Message): Unit = {
      if (isPrimary) {
        onPlugMessage(this, message)
      }
    }

    override def onConnect(node: Node): Unit = onPlugConnect(this, node)

    override def onDisconnect(node: Node): Unit = onPlugDisconnect(this, node)

    def isPrimary = plugs(plugs.indexWhere(_.node.network == node.network)) == this

    def plugsInOtherNetworks = plugs.filter(_.node.network != node.network)
  }

  protected def onPlugConnect(plug: Plug, node: Node): Unit = {}

  protected def onPlugDisconnect(plug: Plug, node: Node): Unit = {}

  protected def onPlugMessage(plug: Plug, message: Message): Unit = {
    if (message.name == "network.message" && !plugs.exists(_.node == message.source)) message.data match {
      case Array(packet: Packet) => tryEnqueuePacket(Option(plug.side), packet)
      case _ =>
    }
  }

  protected def createNode(plug: Plug): Node = api.Network.newNode(plug, Visibility.Network).create()
}
