package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.nbt.CompoundTag

/**
 * 向客户端同步「能量缓冲状态」的方块实体
 * （对应 1.7.10 的 `common.tileentity.traits.PowerInformation`）。
 *
 * 1.21.1 迁移要点：
 *  - `@SideOnly(Side.CLIENT)` 删除；客户端同步数据统一走
 *    `readFromNBTForClient` / `writeToNBTForClient`（由 `BlockEntityBase#getUpdateTag` 驱动）。
 *  - 节流逻辑（每 `100 / tickFrequency` tick 最多同步一次、比例变化超过 5% 立即同步）保持原样。
 *  - 同步通过 `ServerPacketSender.sendPowerState(this)` 发送专用包（对齐 OCCE）。
 */
trait PowerInformation extends TileEntity {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  private var lastSentRatio = -1.0

  private var ticksUntilSync = 0

  def globalBuffer: Double

  def globalBuffer_=(value: Double): Unit

  def globalBufferSize: Double

  def globalBufferSize_=(value: Double): Unit

  protected def updatePowerInformation(): Unit = {
    val ratio = if (globalBufferSize > 0) globalBuffer / globalBufferSize else 0
    if (shouldSync(ratio) || hasChangedSignificantly(ratio)) {
      lastSentRatio = ratio
      ServerPacketSender.sendPowerState(this)
    }
  }

  private def hasChangedSignificantly(ratio: Double) = lastSentRatio < 0 || math.abs(lastSentRatio - ratio) > (5.0 / 100.0)

  private def shouldSync(ratio: Double) = {
    ticksUntilSync -= 1
    if (ticksUntilSync <= 0) {
      ticksUntilSync = (100 / Settings.get.tickFrequency).toInt max 1
      lastSentRatio != ratio
    }
    else false
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    globalBuffer = nbt.getDouble("globalBuffer")
    globalBufferSize = nbt.getDouble("globalBufferSize")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    lastSentRatio = if (globalBufferSize > 0) globalBuffer / globalBufferSize else 0
    nbt.putDouble("globalBuffer", globalBuffer)
    nbt.putDouble("globalBufferSize", globalBufferSize)
  }
}
