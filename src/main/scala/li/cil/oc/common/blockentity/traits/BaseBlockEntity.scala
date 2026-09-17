package li.cil.oc.common.blockentity.traits

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.client.Sound
import li.cil.oc.common.SaveHandler
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SideTracker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.{BlockPos, HolderLookup}
import net.minecraft.world.level.Level
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.Connection
import net.neoforged.neoforge.client.model.data.ModelProperty

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
    // 1.21.1：`saveAdditional` 需要注册表上下文，而 OC 自己的存档钩子
    // （`saveForServer` / `saveForClient`）刻意不带 provider —— 子类里需要编解码物品堆叠的
    // 地方统一走 `li.cil.oc.util.RegistryAccessHelper.getOrEmpty()`。这里同理。
    super.saveAdditional(nbt, li.cil.oc.util.RegistryAccessHelper.getOrEmpty())
  }

  def loadForClient(nbt: CompoundTag): Unit = {}

  def saveForClient(nbt: CompoundTag): Unit = {
    nbt.putBoolean(IsServerDataTag, false)
  }

  // ----------------------------------------------------------------------- //

  // 1.21.1：`BlockEntity#load(CompoundTag)` 已被 `loadAdditional(CompoundTag, HolderLookup.Provider)`
  // 取代（`load` 彻底移除，公开入口是 `loadWithComponents`）。
  override def loadAdditional(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadAdditional(nbt, provider)
    if (isServer || nbt.getBoolean(IsServerDataTag)) {
      loadForServer(nbt)
    }
    else {
      loadForClient(nbt)
    }
  }

  override def saveAdditional(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveAdditional(nbt, provider)
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

  // 1.21.1：`getUpdateTag()` 现在要接收注册表上下文。
  override def getUpdateTag(provider: HolderLookup.Provider): CompoundTag = {
    val nbt = super.getUpdateTag(provider)

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

  // 1.21.1：`IBlockEntityExtension#onDataPacket` 多了注册表上下文参数。
  override def onDataPacket(manager: Connection, packet: ClientboundBlockEntityDataPacket, provider: HolderLookup.Provider): Unit = {
    try loadForClient(packet.getTag) catch {
      case e: Throwable => OpenComputers.log.warn("There was a problem reading a BlockEntity description packet. Please report this if you see it!", e)
    }
  }
  
  def hasProperty(prop: ModelProperty[_]) = false

  def getData[T](prop: ModelProperty[T]): T = null.asInstanceOf[T]

  def setData[T](prop: ModelProperty[T], value: T): T = null.asInstanceOf[T]
}
