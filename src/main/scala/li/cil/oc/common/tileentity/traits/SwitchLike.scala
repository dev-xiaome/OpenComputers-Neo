package li.cil.oc.common.tileentity.traits

import scala.collection.mutable

/**
 * 网络交换机 / 中继器的公共部分（对应 1.7.10 的 `common.tileentity.traits.SwitchLike`）。
 *
 * 1.21.1 迁移要点：
 *  - 原 `PacketSender.sendSwitchActivity(this)` 属于尚未移植的服务端网络层，
 *    见 [[onSwitchActivity]] 的降级说明。
 *  - `relayDelay` / `isWirelessEnabled` / `isLinkedEnabled` 仍为抽象成员，由交换机 / 中继器实现。
 */
trait SwitchLike extends Hub {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  def relayDelay: Int

  def isWirelessEnabled: Boolean

  def isLinkedEnabled: Boolean

  val computers = mutable.Buffer.empty[AnyRef]

  val openPorts = mutable.Map.empty[AnyRef, mutable.Set[Int]]

  var lastMessage = 0L

  def onSwitchActivity(): Unit = {
    val now = System.currentTimeMillis()
    if (now - lastMessage >= (relayDelay - 1) * 50) {
      lastMessage = now
      // 原：PacketSender.sendSwitchActivity(this)
      // TODO(server.PacketSender): 服务端网络层移植后改为发送 SwitchActivity 包
      // （让附近客户端播放交换机的指示灯动画）；目前退化为方块更新包。
      markBlockForUpdate()
    }
  }
}
