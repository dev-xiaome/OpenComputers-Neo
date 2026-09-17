package li.cil.oc.server.network

import li.cil.oc.api.network.Packet

import scala.collection.mutable

object DebugNetwork {
  val cards = mutable.WeakHashMap.empty[DebugNode, Unit]

  def add(card: DebugNode): Unit = {
    // Scala 2.13：`Unit` 伴生对象不能再作为值使用，unit 值统一写作 `()`。
    cards.put(card, ())
  }

  def remove(card: DebugNode): Unit = {
    cards.remove(card)
  }

  def getEndpoint(tunnel: String) = cards.keys.find(_.address == tunnel)

  trait DebugNode {
    def address: String

    def receivePacket(packet: Packet): Unit
  }
}
