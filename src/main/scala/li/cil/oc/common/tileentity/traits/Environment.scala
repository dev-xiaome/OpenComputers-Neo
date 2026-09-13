package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

/**
 * 带组件网络环境的方块实体（对应 1.7.10 的 `common.tileentity.traits.Environment`）。
 *
 * 1.21.1 迁移要点：
 *  - `EventHandler.scheduleServer(this)`（延迟一 tick 入网）→ 直接在 `initialize()` 里
 *    调用 `api.Network.joinOrCreateNetwork(this)`：`BlockEntity#onLoad()` 保证此时方块实体
 *    已完整加入世界，不再需要规避「世界/区块未就绪」。
 *  - 原 `dispose()` 里依赖 `li.cil.oc.server.network.Network` 的「按侧断连」逻辑（用于
 *    可被移动的计算机）尚未移植，这里退化为直接摘除节点，语义等价于节点离开网络。
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`。
 *  - `world.markTileEntityChunkModified(x, y, z, this)` → `setChanged()`。
 */
trait Environment extends TileEntity with network.Environment with network.EnvironmentHost {
  // 注意：Scala 的自类型不会被继承，因此 TileEntity 的每个子 trait 都必须重新声明
  // `self: BlockEntity`，否则编译器无法证明子类型（也就看不到 BlockEntity 的成员）。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  protected var isChangeScheduled = false

  override def xPosition(): Double = x + 0.5

  override def yPosition(): Double = y + 0.5

  override def zPosition(): Double = z + 0.5

  override def markChanged(): Unit =
    if (canUpdate) isChangeScheduled = true
    else setChanged()

  protected def isConnected: Boolean = node != null && node.address != null && node.network != null

  // ----------------------------------------------------------------------- //

  override protected def initialize(): Unit = {
    super.initialize()
    if (isServer) {
      // 原实现：EventHandler.scheduleServer(this)，把入网推迟到下一个服务端 tick。
      // 1.21.1：BlockEntity#onLoad() 已经是「方块实体完整加入世界之后」的时机。
      api.Network.joinOrCreateNetwork(this)
    }
  }

  override def tick(): Unit = {
    super.tick()
    if (isChangeScheduled) {
      setChanged()
      isChangeScheduled = false
    }
  }

  override def dispose(): Unit = {
    super.dispose()
    if (isServer) {
      // TODO(server.network): 原实现在此处对「正在被移动的计算机」逐个方向断开邻居节点
      // （依赖 li.cil.oc.server.network.Network，尚未移植）。这里退化为直接移除节点。
      Option(node).foreach(_.remove())
      this match {
        case sidedEnvironment: SidedEnvironment =>
          for (side <- Direction.values()) {
            Option(sidedEnvironment.sidedNode(side)).foreach(_.remove())
          }
        case _ =>
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (node != null && node.host == this) {
      node.load(nbt.getCompound(Settings.namespace + "node"))
    }
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    if (node != null && node.host == this) {
      nbt.setNewCompoundTag(Settings.namespace + "node", node.save)
    }
  }

  // ----------------------------------------------------------------------- //

  override def onMessage(message: network.Message): Unit = {}

  override def onConnect(node: network.Node): Unit = {}

  override def onDisconnect(node: network.Node): Unit = {
    if (node == this.node) node match {
      case connector: Connector => connector.setLocalBufferSize(0)
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //

  /**
   * 方块实体是否正在被移动（AE2 空间存储等）。
   *
   * 1.7.10 通过 `@Optional.Method(modid = AppliedEnergistics2)` 暴露给 AE2 的
   * `IMovableTile`；1.21.1 移除了 ASM/接口注入，改为普通方法由集成层调用。
   */
  protected var moving = false

  def prepareToMove(): Boolean = {
    moving = true
    true
  }

  def doneMoving(): Unit = {
    moving = false
    api.Network.joinOrCreateNetwork(this)
    markBlockForUpdate()
  }

  // ----------------------------------------------------------------------- //

  protected def result(args: Any*): Array[AnyRef] = li.cil.oc.util.ResultWrapper.result(args: _*)
}
