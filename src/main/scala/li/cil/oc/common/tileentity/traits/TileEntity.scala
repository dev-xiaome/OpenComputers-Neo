package li.cil.oc.common.tileentity.traits

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.client.Sound
import li.cil.oc.common.SaveHandler
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SideTracker
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.S35PacketUpdateTileEntity

trait BlockEntity extends net.minecraft.tileentity.BlockEntity {
  def world = getWorldObj

  def x = xCoord

  def y = yCoord

  def z = zCoord

  def position = BlockPosition(x, y, z, world)

  def block = getBlockType

  def isClient = !isServer

  def isServer = if (world != null) !world.isRemote else SideTracker.isServer

  // ----------------------------------------------------------------------- //

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (Settings.get.periodicallyForceLightUpdate && world.getTotalWorldTime % 40 == 0 && block.getLightValue(world, x, y, z) > 0) {
      world.markBlockForUpdate(x, y, z)
    }
  }

  override def validate(): Unit = {
    super.validate()
    initialize()
  }

  override def invalidate(): Unit = {
    super.invalidate()
    dispose()
  }

  override def onChunkUnload(): Unit = {
    super.onChunkUnload()
    dispose()
  }

  protected def initialize() {}

  def dispose(): Unit = {
    if (isClient) {
      // Note: chunk unload is handled by sound via event handler.
      Sound.stopLoop(this)
    }
  }

  // ----------------------------------------------------------------------- //

  def readFromNBTForServer(nbt: CompoundTag): Unit = super.readFromNBT(nbt)

  def writeToNBTForServer(nbt: CompoundTag): Unit = super.writeToNBT(nbt)

  @SideOnly(Dist.CLIENT)
  def readFromNBTForClient(nbt: CompoundTag) {}

  def writeToNBTForClient(nbt: CompoundTag) {}

  // ----------------------------------------------------------------------- //

  override def readFromNBT(nbt: CompoundTag): Unit = {
    if (isServer) {
      readFromNBTForServer(nbt)
    }
  }

  override def writeToNBT(nbt: CompoundTag): Unit = {
    if (isServer) {
      writeToNBTForServer(nbt)
    }
  }

  override def getDescriptionPacket = {
    val nbt = new CompoundTag()

    // See comment on savingForClients variable.
    SaveHandler.savingForClients = true
    try {
      try writeToNBTForClient(nbt) catch {
        case e: Throwable => OpenComputers.log.warn("There was a problem writing a BlockEntity description packet. Please report this if you see it!", e)
      }
      if (nbt.hasNoTags) null else new S35PacketUpdateTileEntity(x, y, z, -1, nbt)
    } finally {
      SaveHandler.savingForClients = false
    }
  }

  override def onDataPacket(manager: NetworkManager, packet: S35PacketUpdateTileEntity): Unit = {
    try readFromNBTForClient(packet.func_148857_g()) catch {
      case e: Throwable => OpenComputers.log.warn("There was a problem reading a BlockEntity description packet. Please report this if you see it!", e)
    }
  }
}
