package li.cil.oc.common

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.network.internal.FMLProxyPacket
import io.netty.buffer.Unpooled
import li.cil.oc.{OpenComputers, Settings}
import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.world.entity.Entity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.{Level, ServerLevel}
import net.minecraft.core.Direction
import org.apache.logging.log4j.LogManager

import scala.jdk.CollectionConverters._

abstract class PacketBuilder(stream: OutputStream) extends DataOutputStream(stream) {
  def writeTileEntity(t: BlockEntity): Unit = {
    writeInt(t.getWorldObj.provider.dimensionId)
    writeInt(t.xCoord)
    writeInt(t.yCoord)
    writeInt(t.zCoord)
  }

  def writeEntity(e: Entity): Unit = {
    writeInt(e.worldObj.provider.dimensionId)
    writeInt(e.getEntityId)
  }

  def writeDirection(d: Option[Direction]) = d match {
    case Some(side) => writeByte(side.ordinal.toByte)
    case _ => writeByte(-1: Byte)
  }

  def writeItemStack(stack: ItemStack) = {
    val haveStack = stack != null && stack.stackSize > 0
    writeBoolean(haveStack)
    if (haveStack) {
      writeNBT(stack.writeToNBT(new CompoundTag()))
    }
  }

  def writeNBT(nbt: CompoundTag) = {
    val haveNbt = nbt != null
    writeBoolean(haveNbt)
    if (haveNbt) {
      NbtIo.write(nbt, this)
    }
  }

  def writeMedium(v: Int) = {
    writeByte(v & 0xFF)
    writeByte((v >> 8) & 0xFF)
    writeByte((v >> 16) & 0xFF)
  }

  def writePacketType(pt: PacketType.Value) = writeByte(pt.id)

  def sendToAllPlayers() = OpenComputers.channel.sendToAll(packet)

  def sendToPlayersNearEntity(e: Entity, range: Option[Double] = None): Unit = sendToNearbyPlayers(e.worldObj, e.posX, e.posY, e.posZ, range)

  def sendToPlayersNearHost(host: EnvironmentHost, range: Option[Double] = None): Unit = {
    host match {
      case t: BlockEntity => sendToPlayersNearTileEntity(t, range)
      case _ => sendToNearbyPlayers(host.world, host.xPosition, host.yPosition, host.zPosition, range)
    }
  }

  def sendToPlayersNearTileEntity(t: BlockEntity, range: Option[Double] = None) {
    t.getWorldObj match {
      case w: ServerLevel =>
        val chunkX = t.xCoord >> 4
        val chunkZ = t.zCoord >> 4

        val manager = FMLCommonHandler.instance.getMinecraftServerInstance.getConfigurationManager
        var maxPacketRange = range.getOrElse((manager.getViewDistance + 1) * 16.0)
        val maxPacketRangeConfig = Settings.get.maxNetworkClientPacketDistance
        if (maxPacketRangeConfig > 0.0D) {
          maxPacketRange = maxPacketRange min maxPacketRangeConfig
        }
        val maxPacketRangeSq = maxPacketRange * maxPacketRange

        for (e <- w.playerEntities) e match {
          case player: ServerPlayer =>
            if (w.getPlayerManager.isPlayerWatchingChunk(player, chunkX, chunkZ)) {
              if (player.getDistanceSq(t.xCoord + 0.5D, t.yCoord + 0.5D, t.zCoord + 0.5D) <= maxPacketRangeSq)
                sendToPlayer(player)
            }
        }
      case _ => sendToNearbyPlayers(t.getWorldObj, t.xCoord + 0.5D, t.yCoord + 0.5D, t.zCoord + 0.5D, range)
    }
  }

  def sendToNearbyPlayers(world: Level, x: Double, y: Double, z: Double, range: Option[Double]): Unit = {
    val dimension = world.provider.dimensionId
    val server = FMLCommonHandler.instance.getMinecraftServerInstance
    val manager = server.getConfigurationManager

    var maxPacketRange = range.getOrElse((manager.getViewDistance + 1) * 16.0)
    val maxPacketRangeConfig = Settings.get.maxNetworkClientPacketDistance
    if (maxPacketRangeConfig > 0.0D) {
      maxPacketRange = maxPacketRange min maxPacketRangeConfig
    }
    val maxPacketRangeSq = maxPacketRange * maxPacketRange

    for (player <- manager.playerEntityList.map(_.asInstanceOf[ServerPlayer]) if player.dimension == dimension) {
      if (player.getDistanceSq(x, y, z) <= maxPacketRangeSq) {
        sendToPlayer(player)
      }
    }
  }

  def sendToPlayer(player: ServerPlayer) = OpenComputers.channel.sendTo(packet, player)

  def sendToServer() = OpenComputers.channel.sendToServer(packet)

  protected def packet: FMLProxyPacket
}

// Necessary to keep track of the GZIP stream.
abstract class PacketBuilderBase[T <: OutputStream](protected val stream: T) extends PacketBuilder(new BufferedOutputStream(stream)) {
  var tileEntity: Option[BlockEntity] = None

  override def writeTileEntity(t: BlockEntity): Unit = {
    super.writeTileEntity(t)
    if (PacketBuilder.isProfilingEnabled) {
      tileEntity = Option(t)
    }
  }
}

class SimplePacketBuilder(val packetType: PacketType.Value) extends PacketBuilderBase(PacketBuilder.newData(compressed = false)) {
  writeByte(packetType.id)

  override protected def packet = {
    flush()
    val payload = stream.toByteArray
    PacketBuilder.logPacket(packetType, payload.length, tileEntity)
    new FMLProxyPacket(Unpooled.wrappedBuffer(payload), "OpenComputers")
  }
}

class CompressedPacketBuilder(val packetType: PacketType.Value, private val data: ByteArrayOutputStream = PacketBuilder.newData(compressed = true)) extends PacketBuilderBase(new DeflaterOutputStream(data, new Deflater(Deflater.BEST_SPEED))) {
  writeByte(packetType.id)

  override protected def packet = {
    flush()
    stream.finish()
    val payload = data.toByteArray
    PacketBuilder.logPacket(packetType, payload.length, tileEntity)
    new FMLProxyPacket(Unpooled.wrappedBuffer(payload), "OpenComputers")
  }
}

object PacketBuilder {
  val log = LogManager.getLogger(OpenComputers.Name + "-PacketBuilder")
  var isProfilingEnabled = false

  def logPacket(packetType: PacketType.Value, payloadSize: Int, tileEntity: Option[BlockEntity]): Unit = {
    if (PacketBuilder.isProfilingEnabled) {
      tileEntity match {
        case Some(t) => PacketBuilder.log.info(s"Sending: $packetType @ $payloadSize bytes from (${t.xCoord}, ${t.yCoord}, ${t.zCoord}).")
        case _ => PacketBuilder.log.info(s"Sending: $packetType @ $payloadSize bytes.")
      }
    }
  }

  def newData(compressed: Boolean) = {
    val data = new ByteArrayOutputStream
    data.write(if (compressed) 1 else 0)
    data
  }
}