package li.cil.oc.common

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.network.OpenComputersPayload
import net.minecraft.core.Direction
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.server.ServerLifecycleHooks
import org.apache.logging.log4j.LogManager

/**
 * 网络包写出器（1.21.1 / NeoForge 版）。
 *
 * 数据格式与 1.7.10 版完全一致：`DataOutputStream` 写入 `包类型首字节 + 负载`，
 * 压缩包在首字节写 1 并以 Deflater 流包裹（[PacketHandler] 侧对应解压）。
 * 唯一变化的是「载体」：
 *
 *  - 1.7.10：`FMLProxyPacket` + `FMLEventChannel`
 *  - 1.21.1：[[li.cil.oc.common.network.OpenComputersPayload]]（`CustomPacketPayload`）
 *    + [[net.neoforged.neoforge.network.PacketDistributor]]
 *
 * 因此 [payload] 由 `FMLProxyPacket` 改为 `OpenComputersPayload`，
 * 所有 `sendToXxx` 也改为走 `PacketDistributor`。
 */
abstract class PacketBuilder(stream: OutputStream) extends DataOutputStream(stream) {
  /** 写入维度 id：1.21.1 没有数字维度 id，改用维度的 `ResourceLocation`（UTF 字符串）。 */
  def writeDimension(level: Level): Unit = {
    writeUTF(if (level != null) level.dimension().location().toString else "")
  }

  def writeTileEntity(t: BlockEntity): Unit = {
    writeDimension(t.getLevel)
    val pos = t.getBlockPos
    writeInt(pos.getX)
    writeInt(pos.getY)
    writeInt(pos.getZ)
  }

  def writeEntity(e: Entity): Unit = {
    writeDimension(e.level())
    writeInt(e.getId)
  }

  def writeDirection(d: Option[Direction]): Unit = d match {
    case Some(side) => writeByte(side.ordinal.toByte)
    case _ => writeByte(-1: Byte)
  }

  def writeItemStack(stack: ItemStack): Unit = {
    // 1.21.1：空堆栈是 ItemStack.EMPTY，不再用 null / stackSize == 0 表示。
    val haveStack = stack != null && !stack.isEmpty
    writeBoolean(haveStack)
    if (haveStack) {
      // 1.21.1 的物品数据走数据组件；save 的静态返回类型是 Tag，这里实际得到 CompoundTag。
      stack.save(RegistryAccess.EMPTY, new CompoundTag()) match {
        case tag: CompoundTag => writeNBT(tag)
        case _ => writeNBT(null)
      }
    }
  }

  def writeNBT(nbt: CompoundTag): Unit = {
    val haveNbt = nbt != null
    writeBoolean(haveNbt)
    if (haveNbt) {
      NbtIo.write(nbt, this)
    }
  }

  def writeMedium(v: Int): Unit = {
    writeByte(v & 0xFF)
    writeByte((v >> 8) & 0xFF)
    writeByte((v >> 16) & 0xFF)
  }

  def writePacketType(pt: PacketType.Value): Unit = writeByte(pt.id)

  def sendToAllPlayers(): Unit = PacketDistributor.sendToAllPlayers(payload)

  def sendToPlayersNearEntity(e: Entity, range: Option[Double] = None): Unit = sendToNearbyPlayers(e.level(), e.getX, e.getY, e.getZ, range)

  def sendToPlayersNearHost(host: EnvironmentHost, range: Option[Double] = None): Unit = {
    host match {
      case t: BlockEntity => sendToPlayersNearTileEntity(t, range)
      case _ => sendToNearbyPlayers(host.world, host.xPosition, host.yPosition, host.zPosition, range)
    }
  }

  /**
   * 发送给方块实体附近的玩家。
   *
   * 1.7.10 版在这里手工做了「区块是否被玩家加载 + 距离」两项检查；
   * NeoForge 的 `PacketDistributor.sendToPlayersNear` 已经处理了维度与距离，
   * 因此这里只需要把原版的视野距离/配置上限换算成半径传进去。
   */
  def sendToPlayersNearTileEntity(t: BlockEntity, range: Option[Double] = None): Unit = {
    t.getLevel match {
      case w: ServerLevel =>
        val pos = t.getBlockPos
        PacketDistributor.sendToPlayersNear(w, null, pos.getX + 0.5D, pos.getY + 0.5D, pos.getZ + 0.5D, maxPacketRange(range), payload)
      case level =>
        if (level == null) {
          PacketBuilder.log.debug("Not sending packet for an unloaded block entity: {}.", t)
        } else {
          PacketBuilder.log.debug("Not sending packet for a block entity in a non-server level: {}.", level)
        }
    }
  }

  def sendToNearbyPlayers(world: Level, x: Double, y: Double, z: Double, range: Option[Double]): Unit = {
    world match {
      case w: ServerLevel =>
        PacketDistributor.sendToPlayersNear(w, null, x, y, z, maxPacketRange(range), payload)
      case _ => // 客户端没有「附近玩家」的概念，静默忽略。
    }
  }

  def sendToPlayer(player: ServerPlayer): Unit = PacketDistributor.sendToPlayer(player, payload)

  def sendToServer(): Unit = PacketDistributor.sendToServer(payload)

  /** 计算实际发送半径：取调用方给的范围（默认按服务端视野距离），再套配置里的上限。 */
  protected def maxPacketRange(range: Option[Double]): Double = {
    var maxPacketRange = range.getOrElse(PacketBuilder.defaultPacketRange)
    val maxPacketRangeConfig = Settings.get.maxNetworkClientPacketDistance
    if (maxPacketRangeConfig > 0.0D) {
      maxPacketRange = maxPacketRange min maxPacketRangeConfig
    }
    maxPacketRange
  }

  /** 待发送的负载（每次访问都会 flush 并序列化当前已写入的内容）。 */
  protected def payload: OpenComputersPayload
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

  override protected def payload: OpenComputersPayload = {
    flush()
    val data = stream.toByteArray
    PacketBuilder.logPacket(packetType, data.length, tileEntity)
    new OpenComputersPayload(data)
  }
}

class CompressedPacketBuilder(val packetType: PacketType.Value, private val data: ByteArrayOutputStream = PacketBuilder.newData(compressed = true)) extends PacketBuilderBase(new DeflaterOutputStream(data, new Deflater(Deflater.BEST_SPEED))) {
  writeByte(packetType.id)

  override protected def payload: OpenComputersPayload = {
    flush()
    stream.finish()
    val compressed = data.toByteArray
    PacketBuilder.logPacket(packetType, compressed.length, tileEntity)
    new OpenComputersPayload(compressed)
  }
}

object PacketBuilder {
  val log = LogManager.getLogger(OpenComputers.Name + "-PacketBuilder")
  var isProfilingEnabled = false

  /** 默认发送半径：原版是 `(视野距离 + 1) * 16`，1.21.1 从当前服务端取视野距离。 */
  def defaultPacketRange: Double = {
    val server = ServerLifecycleHooks.getCurrentServer
    if (server == null) 128.0D
    else (server.getPlayerList.getViewDistance + 1) * 16.0D
  }

  def logPacket(packetType: PacketType.Value, payloadSize: Int, tileEntity: Option[BlockEntity]): Unit = {
    if (PacketBuilder.isProfilingEnabled) {
      tileEntity match {
        case Some(t) =>
          val pos = t.getBlockPos
          PacketBuilder.log.info(s"Sending: $packetType @ $payloadSize bytes from (${pos.getX}, ${pos.getY}, ${pos.getZ}).")
        case _ => PacketBuilder.log.info(s"Sending: $packetType @ $payloadSize bytes.")
      }
    }
  }

  def newData(compressed: Boolean): ByteArrayOutputStream = {
    val data = new ByteArrayOutputStream
    data.write(if (compressed) 1 else 0)
    data
  }
}
