package li.cil.oc.server.agent

import io.netty.channel.Channel
import net.minecraft.network.{Connection, PacketSendListener}
import net.minecraft.network.protocol.{Packet, PacketFlow}

/**
 * 机器人 / 无人机假玩家的「空网络连接」（对应 OCCE 的同名单例）。
 *
 * ==1.7.10 到 1.21.1 的 API 翻译==
 *  - `net.minecraft.network.NetworkManager` 变成 `net.minecraft.network.Connection`；
 *    `scheduleOutboundPacket(packet, listeners...)` 变成 `send(packet[, listener[, flush]])`。
 *  - 上游（1.7.10 与 OCCE）都会在构造假玩家时替换网络层：1.7.10 是
 *    `playerNetServerHandler = new NetHandlerPlayServer(mcServer, new FakeNetworkManager, this)`，
 *    OCCE 是 `connection = new ServerGamePacketListenerImpl(server, FakeNetworkManager, this)`。
 *
 * ==本移植为什么不替换 `connection`==
 *  1.21.1 的 `ServerGamePacketListenerImpl` 构造签名是
 *  `(MinecraftServer, Connection, ServerPlayer, CommonListenerCookie)`，多出的 cookie 可以用
 *  `CommonListenerCookie.createInitial(profile, transferred)` 构造出来；但 NeoForge 的
 *  `FakePlayer` 在构造时已经自建了 `FakePlayerNetHandler`，其内部就是一个只丢弃出站包的私有
 *  `FakeConnection`（`PacketFlow.SERVERBOUND`、`isConnected` 恒为 false），语义与这里的空连接
 *  完全等价，而且它还覆写了更多 `ServerGamePacketListenerImpl` 的入口，比换成裸连接更安全。
 *  因此 [[Player]] 不再替换 `connection`，本类保留下来供「确实需要显式替换连接时」使用，
 *  形态与 OCCE 保持一致（单例）。
 *
 *  `PacketFlow.SERVERBOUND` 表示这条连接接收来自客户端的包，与 NeoForge 的
 *  `FakePlayer.FakeConnection` 一致（OCCE 里写的是 `CLIENTBOUND`）。
 */
object FakeNetworkManager extends Connection(PacketFlow.SERVERBOUND) {
  override def send(packet: Packet[_]): Unit = {}

  override def send(packet: Packet[_], listener: PacketSendListener): Unit = {}

  /**
   * 三参重载也要一并吞掉。
   *
   * `Connection#send(packet, listener, flush)` 在 `isConnected` 为 false 时会把发包动作压进
   * `pendingActions` 队列，如果只覆写前两个重载，这个队列会随着假玩家的交互持续增长。
   */
  override def send(packet: Packet[_], listener: PacketSendListener, flush: Boolean): Unit = {}

  override def flushChannel(): Unit = {}

  override def isConnected: Boolean = false

  override def isConnecting: Boolean = false

  override def channel(): Channel = null
}
