package li.cil.oc.common

import li.cil.oc.Settings
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.util.BlockPosition
import net.minecraft.core.{Direction, Registry, RegistryAccess}
import net.minecraft.nbt.{CompoundTag, NbtIo}
import net.minecraft.server.level.{ServerLevel, ServerPlayer}
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.{ChunkPos, Level}
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.server.ServerLifecycleHooks

import java.io.{
  BufferedOutputStream,
  ByteArrayOutputStream,
  DataOutputStream,
  OutputStream
}
import java.util.zip.{Deflater, DeflaterOutputStream}

import scala.jdk.CollectionConverters._

abstract class PacketBuilder(stream: OutputStream) extends DataOutputStream(stream) {
  def writeRegistryEntry[T](registry: Registry[T], value: T): Unit = {
    val key = registry.getKey(value)
    if (key != null) {
      writeUTF(key.toString)
    } else {
      writeUTF("minecraft:air")
    }
  }

  def writeTileEntity(t: BlockEntity): Unit = {
    writeUTF(t.getLevel.dimension.location.toString)
    writeInt(t.getBlockPos.getX)
    writeInt(t.getBlockPos.getY)
    writeInt(t.getBlockPos.getZ)
  }

  def writeEntity(e: Entity): Unit = {
    writeUTF(e.level.dimension.location.toString)
    writeInt(e.getId)
  }

  def writeDirection(d: Option[Direction]) = d match {
    case Some(side) => writeByte(side.ordinal.toByte)
    case _ => writeByte(-1: Byte)
  }

  def writeItemStack(stack: ItemStack, registryAccess: RegistryAccess): Unit = {
    val haveStack = !stack.isEmpty && stack.getCount > 0
    writeBoolean(haveStack)
    if (haveStack) {
      writeNBT(stack.save(registryAccess).asInstanceOf[CompoundTag])
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
  
  def writeBlockPosCoords(pos: BlockPosition) = {
    writeInt(pos.x)
    writeInt(pos.y)
    writeInt(pos.z)
  }

  def writePacketType(pt: PacketType.Value) = writeByte(pt.id)
  
  def sendToPlayersNearEntity(e: Entity, range: Option[Double] = None): Unit = sendToNearbyPlayers(e.level, e.getX, e.getY, e.getZ, range)

  def sendToPlayersNearHost(host: EnvironmentHost, range: Option[Double] = None): Unit = {
    host match {
      case t: BlockEntity => sendToPlayersNearTileEntity(t, range)
      case _ => sendToNearbyPlayers(host.getEnvironmentLevel, host.xPosition, host.yPosition, host.zPosition, range)
    }
  }

  def sendToPlayersNearTileEntity(t: BlockEntity, range: Option[Double] = None): Unit = {
    t.getLevel match {
      case w: ServerLevel =>
        val chunk = new ChunkPos(t.getBlockPos)

        val manager = ServerLifecycleHooks.getCurrentServer.getPlayerList
        var maxPacketRange = range.getOrElse((manager.getViewDistance + 1) * 16.0)
        val maxPacketRangeConfig = Settings.get.maxNetworkClientPacketDistance
        if (maxPacketRangeConfig > 0.0D) {
          maxPacketRange = maxPacketRange min maxPacketRangeConfig
        }
        val maxPacketRangeSq = maxPacketRange * maxPacketRange

        w.getChunkSource.chunkMap.getPlayers(chunk, false).forEach {
          case player =>
            if (player.distanceToSqr(t.getBlockPos.getX + 0.5D, t.getBlockPos.getY + 0.5D, t.getBlockPos.getZ + 0.5D) <= maxPacketRangeSq)
              sendToPlayer(player)
        }
      case _ => sendToNearbyPlayers(t.getLevel, t.getBlockPos.getX + 0.5D, t.getBlockPos.getY + 0.5D, t.getBlockPos.getZ + 0.5D, range)
    }
  }

  def sendToNearbyPlayers(world: Level, x: Double, y: Double, z: Double, range: Option[Double]): Unit = {
    val server = ServerLifecycleHooks.getCurrentServer
    val manager = server.getPlayerList

    var maxPacketRange = range.getOrElse((manager.getViewDistance + 1) * 16.0)
    val maxPacketRangeConfig = Settings.get.maxNetworkClientPacketDistance
    if (maxPacketRangeConfig > 0.0D) {
      maxPacketRange = maxPacketRange min maxPacketRangeConfig
    }
    val maxPacketRangeSq = maxPacketRange * maxPacketRange

    for (player <- manager.getPlayers.asScala if player.level == world) {
      if (player.distanceToSqr(x, y, z) <= maxPacketRangeSq) {
        sendToPlayer(player)
      }
    }
  }

  def sendToAllPlayers(): Unit =
    PacketDistributor.sendToAllPlayers(new PacketPayload(packet))

  def sendToPlayer(player: ServerPlayer): Unit =
    PacketDistributor.sendToPlayer(player, new PacketPayload(packet))

  def sendToServer(): Unit =
    PacketDistributor.sendToServer(new PacketPayload(packet))

  protected def packet: Array[Byte]
}

// Necessary to keep track of the GZIP stream.
abstract class PacketBuilderBase[T <: OutputStream](protected val stream: T) extends PacketBuilder(new BufferedOutputStream(stream))

class SimplePacketBuilder(val packetType: PacketType.Value) extends PacketBuilderBase(PacketBuilder.newData(compressed = false)) {
  writeByte(packetType.id)

  override protected def packet = {
    flush()
    stream.toByteArray
  }
}

class CompressedPacketBuilder(val packetType: PacketType.Value, private val data: ByteArrayOutputStream = PacketBuilder.newData(compressed = true)) extends PacketBuilderBase(new DeflaterOutputStream(data, new Deflater(Deflater.BEST_SPEED))) {
  writeByte(packetType.id)

  override protected def packet = {
    flush()
    stream.finish()
    data.toByteArray
  }
}

object PacketBuilder {
  def newData(compressed: Boolean) = {
    val data = new ByteArrayOutputStream
    data.write(if (compressed) 1 else 0)
    data
  }
}
