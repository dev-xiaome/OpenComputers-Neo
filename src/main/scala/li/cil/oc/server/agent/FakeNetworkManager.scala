package li.cil.oc.server.agent

import io.netty.channel.Channel
import net.minecraft.network.{Connection, PacketSendListener}
import net.minecraft.network.protocol.{Packet, PacketFlow}

/**
 * 机器人 / 无人机假玩家的「空网络连接」。
 *
 * ==1.7.10 → 1.21.1==
 *  - `net.minecraft.network.NetworkManager` → `net.minecraft.network.Connection`；
 *    `scheduleOutboundPacket(packet, listeners*)` → `send(packet[, listener[, flush]])`。
 *  - 原实现只覆写「发包」入口，让假玩家的所有出站包被静默丢弃；这里保持同样的策略。
 *
 * TODO(port): 1.21.1 的 `ServerPlayer#connection` 是 `ServerGamePacketListenerImpl`，
 *  其构造签名已变为 `(MinecraftServer, Connection, ServerPlayer, CommonListenerCookie)`，
 *  最后一个参数没有可用的廉价构造方式；因此 [[Player]] **不再**替换假玩家的
 *  `connection` 字段（1.7.10 里对应 `playerNetServerHandler = new NetHandlerPlayServer(...)`）。
 *  NeoForge 的 `FakePlayer` 由 `ServerPlayer` 构造时自建的 `Connection` 承载，其
 *  `isConnected()` 恒为 false，出站包只会进队列而不会被真正发送，效果与 1.7.10 的空
 *  NetworkManager 基本等价。本类保留下来作为「如需显式替换连接时的现成实现」。
 */
class FakeNetworkManager extends Connection(PacketFlow.SERVERBOUND) {
  override def send(packet: Packet[_]): Unit = {}

  override def send(packet: Packet[_], listener: PacketSendListener): Unit = {}

  override def send(packet: Packet[_], listener: PacketSendListener, flush: Boolean): Unit = {}

  override def flushChannel(): Unit = {}

  override def isConnected: Boolean = false

  override def isConnecting: Boolean = false

  override def channel(): Channel = null
}
