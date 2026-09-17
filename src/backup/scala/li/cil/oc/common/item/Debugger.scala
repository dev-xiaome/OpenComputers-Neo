package li.cil.oc.common.item

import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.api.network._
import li.cil.oc.util.BlockPosition
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「网络调试器」（原 `li.cil.oc.common.item.Debugger`）。
 *
 * 1.21.1 迁移要点：
 *  - `world.getTileEntity(pos)` → `world.getBlockEntity(pos)`
 *  - `Direction.getOrientation(side)` → `Direction.from3DDataValue(side)`
 *  - `FakePlayer`（Forge）→ 不再特判，只对 `ServerPlayer` 生效
 *  - `world.isRemote` → `world.isClientSide`
 */
class Debugger(props: Item.Properties) extends Item(props) with traits.Delegate {

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition,
                         side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    position.world match {
      case Some(world) =>
        player match {
          case _: ServerPlayer =>
            world.getBlockEntity(position.toChunkCoordinates) match {
              case host: SidedEnvironment =>
                Debugger.reconnect(Array(host.sidedNode(Direction.from3DDataValue(side))))
                true
              case host: Environment =>
                Debugger.reconnect(Array(host.node))
                true
              case _ =>
                Debugger.node.remove()
                true
            }
          case _ => false
        }
      case _ => false
    }
  }
}

object Debugger extends Environment {
  var node: Node = api.Network.newNode(this, Visibility.Network).create()

  override def onConnect(node: Node): Unit = {
    OpenComputers.log.info(s"[NETWORK DEBUGGER] New node in network: ${nodeInfo(node)}")
  }

  override def onDisconnect(node: Node): Unit = {
    OpenComputers.log.info(s"[NETWORK DEBUGGER] Node removed from network: ${nodeInfo(node)}")
  }

  override def onMessage(message: Message): Unit = {
    OpenComputers.log.info(s"[NETWORK DEBUGGER] Received message: ${messageInfo(message)}.")
  }

  def reconnect(nodes: Array[Node]): Unit = {
    node.remove()
    api.Network.joinNewNetwork(node)
    for (node <- nodes if node != null) {
      this.node.connect(node)
    }
  }

  private def nodeInfo(node: Node): String = s"{address = ${node.address}, reachability = ${node.reachability.name}" + (node match {
    case componentConnector: ComponentConnector => componentInfo(componentConnector) + connectorInfo(componentConnector)
    case component: Component => componentInfo(component)
    case connector: Connector => connectorInfo(connector)
    case _ =>
  }) + "}"

  private def componentInfo(component: Component): String =
    s", type = component, name = ${component.name}, visibility = ${component.visibility.name}"

  private def connectorInfo(connector: Connector): String =
    s", type = connector, buffer = ${connector.localBuffer}, bufferSize = ${connector.localBufferSize}"

  private def messageInfo(message: Message): String =
    s"{name = ${message.name()}, source = ${nodeInfo(message.source)}, data = [${message.data.mkString(", ")}]}"
}
