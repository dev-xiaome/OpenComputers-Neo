package li.cil.oc.common.blockentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.network
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.common.EventHandler
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraftforge.client.model.data.{ModelData, ModelProperty}

trait Environment extends BaseBlockEntity with network.Environment with network.EnvironmentHost {
  protected var isChangeScheduled = false

  override def getEnvironmentLevel: Level = getLevel

  override def xPosition: Double = x + 0.5

  override def yPosition: Double = y + 0.5

  override def zPosition: Double = z + 0.5

  override def markChanged(): Unit = if (this.isInstanceOf[Tickable]) isChangeScheduled = true else this.setChanged()

  protected def isConnected: Boolean = node != null && node.address != null && node.network != null

  // ----------------------------------------------------------------------- //

  override protected def initialize(): Unit = {
    super.initialize()
    if (isServer) {
      EventHandler.scheduleServer(this)
    }
  }

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (isChangeScheduled) {
      this.setChanged()
      isChangeScheduled = false
    }
  }

  override def dispose(): Unit = {
    super.dispose()
    if (isServer) {
      Option(node).foreach(_.remove)
      this match {
        case sidedEnvironment: SidedEnvironment => for (side <- Direction.values) {
          Option(sidedEnvironment.sidedNode(side)).foreach(_.remove())
        }
        case _ =>
      }
    }
  }

  // ----------------------------------------------------------------------- //

  private final val NodeTag = Settings.namespace + "node"

  override def loadForServer(nbt: CompoundTag): Unit = {
    super.loadForServer(nbt)
    if (node != null && node.host == this) {
      node.loadData(nbt.getCompound(NodeTag))
    }
  }

  override def saveForServer(nbt: CompoundTag): Unit = {
    super.saveForServer(nbt)
    if (node != null && node.host == this) {
      nbt.setNewCompoundTag(NodeTag, node.saveData)
    }
  }

  // ----------------------------------------------------------------------- //

  override def onMessage(message: network.Message): Unit = {}

  override def onConnect(node: network.Node): Unit = {}

  override def onDisconnect(node: network.Node): Unit = {
    if (node == this.node) node match {
      case connector: Connector =>
        // Set it to zero to push all energy into other nodes, to
        // avoid energy loss when removing nodes. Set it back to the
        // original value though, as there are cases where the node
        // is re-used afterwards, without re-adjusting its buffer size.
        var bufferSize = connector.localBufferSize()
        connector.setLocalBufferSize(0)
        connector.setLocalBufferSize(bufferSize)
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //

  protected def result(args: Any*) = li.cil.oc.util.ResultWrapper.result(args: _*)

  // ----------------------------------------------------------------------- //

  override def getModelData: ModelData = ModelData.EMPTY

  @Deprecated
  override def hasProperty(prop: ModelProperty[_]) = false

  @Deprecated
  override def getData[T](prop: ModelProperty[T]): T = null.asInstanceOf[T]

  @Deprecated
  override def setData[T](prop: ModelProperty[T], value: T): T = null.asInstanceOf[T]
}
