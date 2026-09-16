package li.cil.oc.common.blockentity.traits

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.client.Sound
import li.cil.oc.common.SaveHandler
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SideTracker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.Connection
import net.minecraftforge.client.model.data.ModelProperty

trait BaseBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
  private final val IsServerDataTag = Settings.namespace + "isServerData"

  def x: Int = getBlockPos.getX

  def y: Int = getBlockPos.getY

  def z: Int = getBlockPos.getZ

  def position = BlockPosition(x, y, z, getLevel)

  def isClient: Boolean = !isServer

  def isServer: Boolean = if (getLevel != null) !getLevel.isClientSide else SideTracker.isServer

  // ----------------------------------------------------------------------- //

  def updateEntity(): Unit = {
    if (Settings.get.periodicallyForceLightUpdate && getLevel.getGameTime % 40 == 0 && getBlockState.getLightEmission(getLevel, getBlockPos) > 0) {
      getLevel.sendBlockUpdated(getBlockPos, getLevel.getBlockState(getBlockPos), getLevel.getBlockState(getBlockPos), 3)
    }
  }

  override def clearRemoved(): Unit = {
    super.clearRemoved()
    initialize()
  }

  override def setRemoved(): Unit = {
    super.setRemoved()
    dispose()
  }

  override def onChunkUnloaded(): Unit = {
    super.onChunkUnloaded()
    try dispose() catch {
      case t: Throwable => OpenComputers.log.error("Failed properly disposing a block entity, things may leak and or break.", t)
    }
  }

  protected def initialize(): Unit = {
  }

  def dispose(): Unit = {
    if (isClient) {
      // Note: chunk unload is handled by sound via event handler.
      Sound.stopLoop(this)
    }
  }

  // ----------------------------------------------------------------------- //

  def loadForServer(nbt: CompoundTag): Unit = {}

  def saveForServer(nbt: CompoundTag): Unit = {
    nbt.putBoolean(IsServerDataTag, true)
    super.saveAdditional(nbt)
  }

  def loadForClient(nbt: CompoundTag): Unit = {}

  def saveForClient(nbt: CompoundTag): Unit = {
    nbt.putBoolean(IsServerDataTag, false)
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    if (isServer || nbt.getBoolean(IsServerDataTag)) {
      loadForServer(nbt)
    }
    else {
      loadForClient(nbt)
    }
  }

  override def saveAdditional(nbt: CompoundTag): Unit = {
    super.saveAdditional(nbt)
    save(nbt)
  }

  def save(nbt: CompoundTag): CompoundTag = {
    if (isServer) {
      saveForServer(nbt)
    }
    nbt
  }

  override def getUpdatePacket: ClientboundBlockEntityDataPacket = {
    ClientboundBlockEntityDataPacket.create(this)
  }

  override def getUpdateTag: CompoundTag = {
    val nbt = super.getUpdateTag

    // See comment on savingForClients variable.
    SaveHandler.savingForClients = true
    try {
      try saveForClient(nbt) catch {
        case e: Throwable => OpenComputers.log.warn("There was a problem writing a BlockEntity description packet. Please report this if you see it!", e)
      }
    } finally {
      SaveHandler.savingForClients = false
    }

    nbt
  }

  override def onDataPacket(manager: Connection, packet: ClientboundBlockEntityDataPacket): Unit = {
    try loadForClient(packet.getTag) catch {
      case e: Throwable => OpenComputers.log.warn("There was a problem reading a BlockEntity description packet. Please report this if you see it!", e)
    }
  }
  
  def hasProperty(prop: ModelProperty[_]) = false

  def getData[T](prop: ModelProperty[T]): T = null.asInstanceOf[T]

  def setData[T](prop: ModelProperty[T], value: T): T = null.asInstanceOf[T]
}
