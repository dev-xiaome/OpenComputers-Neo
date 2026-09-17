package li.cil.oc.common.component

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.{CompressedPacketBuilder, PacketBuilder, PacketType, SimplePacketBuilder, Tier}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level

/**
 * 文本缓冲 / 屏幕相关的网络发送器。
 *
 * 对应 1.7.10 的 `li.cil.oc.server.PacketSender` 中与文本缓冲有关的部分。
 * `li.cil.oc.server` 包（ComponentTracker / PacketSender）本轮尚未移植，
 * 但报文格式与 [[li.cil.oc.common.PacketBuilder]] 已经完全就位，
 * 因此这里把这几条报文的**写包逻辑原样搬过来**，等 `server.PacketSender`
 * 移植完成后可整体迁移回去（见 `TODO(server.packet)`）。
 *
 * 所有 `append*` 方法与旧版保持字节级一致，「多包合并」时由
 * [[li.cil.oc.common.component.TextBuffer]] 在每 tick 末尾统一发出。
 */
object ServerPacketSender {

  // ----------------------------------------------------------------------- //
  // 单发报文
  // ----------------------------------------------------------------------- //

  /** 屏幕供电状态变化。 */
  def sendTextBufferPowerChange(address: String, hasPower: Boolean, host: EnvironmentHost): Unit = {
    val pb = new SimplePacketBuilder(PacketType.TextBufferPowerChange)

    pb.writeUTF(address)
    pb.writeBoolean(hasPower)

    pb.sendToPlayersNearHost(host)
  }

  /**
   * 屏幕 «反向触摸模式» 变化。
   *
   * TODO(server.packet): 旧版签名是 `sendScreenTouchMode(t: tileentity.Screen, value: Boolean)`
   * 并调用 `pb.writeTileEntity(t)`；`common/tileentity` 由其它施工者负责，
   * 这里先接受坐标参数，避免对未移植类的依赖。
   */
  def sendScreenTouchMode(level: Level, x: Int, y: Int, z: Int, value: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.ScreenTouchMode)

    pb.writeDimension(level)
    pb.writeInt(x)
    pb.writeInt(y)
    pb.writeInt(z)
    pb.writeBoolean(value)

    pb.sendToNearbyPlayers(level, x + 0.5D, y + 0.5D, z + 0.5D, None)
  }

  /**
   * 向指定玩家发送文本缓冲的完整初始化数据。
   *
   * TODO(server.packet): 依赖 `ServerPlayer`，等网络层的「按玩家回包」就位后，
   * 由包处理入口（`PacketHandler`）调用。
   */
  def sendTextBufferInit(address: String, value: CompoundTag, player: net.minecraft.server.level.ServerPlayer): Unit = {
    val pb = new CompressedPacketBuilder(PacketType.TextBufferInit)

    pb.writeUTF(address)
    pb.writeNBT(value)

    pb.sendToPlayer(player)
  }

  // ----------------------------------------------------------------------- //
  // 合并进 TextBufferMulti 的片段
  // ----------------------------------------------------------------------- //

  def appendTextBufferColorChange(pb: PacketBuilder, foreground: li.cil.oc.util.PackedColor.Color, background: li.cil.oc.util.PackedColor.Color): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiColorChange)

    pb.writeInt(foreground.value)
    pb.writeBoolean(foreground.isPalette)
    pb.writeInt(background.value)
    pb.writeBoolean(background.isPalette)
  }

  def appendTextBufferCopy(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiCopy)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeInt(tx)
    pb.writeInt(ty)
  }

  def appendTextBufferDepthChange(pb: PacketBuilder, value: api.internal.TextBuffer.ColorDepth): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiDepthChange)

    pb.writeInt(value.ordinal)
  }

  def appendTextBufferFill(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, c: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiFill)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeMedium(c)
  }

  def appendTextBufferPaletteChange(pb: PacketBuilder, index: Int, color: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiPaletteChange)

    pb.writeInt(index)
    pb.writeInt(color)
  }

  def appendTextBufferResolutionChange(pb: PacketBuilder, w: Int, h: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  def appendTextBufferViewportResolutionChange(pb: PacketBuilder, w: Int, h: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiViewportResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  def appendTextBufferMaxResolutionChange(pb: PacketBuilder, w: Int, h: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiMaxResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  def appendTextBufferSet(pb: PacketBuilder, col: Int, row: Int, s: String, vertical: Boolean): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiSet)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeUTF(s)
    pb.writeBoolean(vertical)
  }

  def appendTextBufferBitBlt(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, owner: String, id: Int, fromCol: Int, fromRow: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferBitBlt)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeUTF(owner)
    pb.writeInt(id)
    pb.writeInt(fromCol)
    pb.writeInt(fromRow)
  }

  def appendTextBufferRamInit(pb: PacketBuilder, address: String, id: Int, nbt: CompoundTag): Unit = {
    pb.writePacketType(PacketType.TextBufferRamInit)

    pb.writeUTF(address)
    pb.writeInt(id)
    pb.writeNBT(nbt)
  }

  def appendTextBufferRamDestroy(pb: PacketBuilder, owner: String, id: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferRamDestroy)
    pb.writeUTF(owner)
    pb.writeInt(id)
  }

  def appendTextBufferRawSetText(pb: PacketBuilder, col: Int, row: Int, text: Array[Array[Int]]): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiRawSetText)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(text.length.toShort)
    for (y <- 0 until text.length.toShort) {
      val line = text(y)
      pb.writeShort(line.length.toShort)
      for (x <- 0 until line.length.toShort) {
        pb.writeMedium(line(x))
      }
    }
  }

  def appendTextBufferRawSetBackground(pb: PacketBuilder, col: Int, row: Int, color: Array[Array[Int]]): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiRawSetBackground)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(color.length.toShort)
    for (y <- 0 until color.length.toShort) {
      val line = color(y)
      pb.writeShort(line.length.toShort)
      for (x <- 0 until line.length.toShort) {
        pb.writeInt(line(x))
      }
    }
  }

  def appendTextBufferRawSetForeground(pb: PacketBuilder, col: Int, row: Int, color: Array[Array[Int]]): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiRawSetForeground)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(color.length.toShort)
    for (y <- 0 until color.length.toShort) {
      val line = color(y)
      pb.writeShort(line.length.toShort)
      for (x <- 0 until line.length.toShort) {
        pb.writeInt(line(x))
      }
    }
  }
}
