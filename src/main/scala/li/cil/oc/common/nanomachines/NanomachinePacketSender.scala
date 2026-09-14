package li.cil.oc.common.nanomachines

import li.cil.oc.api
import li.cil.oc.common.{CompressedPacketBuilder, PacketType, SimplePacketBuilder}
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * 纳米机器相关的网络发送器。
 *
 * 对应 1.7.10 `li.cil.oc.server.PacketSender` 中与纳米机器有关的部分。
 * `li.cil.oc.server` 包本轮尚未移植，但报文格式（[[li.cil.oc.common.PacketType]]）
 * 与写包器（[[li.cil.oc.common.PacketBuilder]]）已经就位，
 * 因此这里把这几条报文的**写包逻辑原样搬过来**，
 * 等 `server.PacketSender` 移植完成后可整体迁移回去（见 `TODO(server.packet)`）。
 */
object NanomachinePacketSender {

  /** 同步完整的控制器配置（含神经连接图）。 */
  def sendNanomachineConfiguration(player: Player): Unit = {
    val pb = new SimplePacketBuilder(PacketType.NanomachinesConfiguration)

    pb.writeEntity(player)
    api.Nanomachines.getController(player) match {
      case controller: ControllerImpl =>
        pb.writeBoolean(true)
        val nbt = new CompoundTag()
        controller.save(nbt)
        pb.writeNBT(nbt)
      case _ =>
        pb.writeBoolean(false)
    }

    pb.sendToPlayersNearEntity(player)
  }

  /** 同步各输入开关状态。 */
  def sendNanomachineInputs(player: Player): Unit = {
    api.Nanomachines.getController(player) match {
      case controller: ControllerImpl =>
        val pb = new SimplePacketBuilder(PacketType.NanomachinesInputs)

        pb.writeEntity(player)
        val inputs = controller.configuration.triggers.map(i => if (i.isActive) 1.toByte else 0.toByte).toArray
        pb.writeInt(inputs.length)
        pb.write(inputs)

        pb.sendToPlayersNearEntity(player)
      case _ => // 没有控制器，什么也不做。
    }
  }

  /** 同步控制器内部能量缓冲。 */
  def sendNanomachinePower(player: Player): Unit = {
    api.Nanomachines.getController(player) match {
      case controller: ControllerImpl =>
        val pb = new SimplePacketBuilder(PacketType.NanomachinesPower)

        pb.writeEntity(player)
        pb.writeDouble(controller.getLocalBuffer)

        pb.sendToPlayersNearEntity(player)
      case _ => // 没有控制器，什么也不做。
    }
  }

  /**
   * 向客户端日志窗口推送一行文本（调试用）。
   *
   * 1.7.10 的 `PacketSender.sendClientLog(line, player)`；报文类型与压缩方式保持一致。
   */
  def sendClientLog(line: String, player: ServerPlayer): Unit = {
    val pb = new CompressedPacketBuilder(PacketType.ClientLog)

    pb.writeUTF(line)

    pb.sendToPlayer(player)
  }
}
