package li.cil.oc.common.item

import li.cil.oc.{api, OpenComputers}
import li.cil.oc.api.network._
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.UseOnContext
import net.neoforged.neoforge.common.extensions.IItemExtension
import net.neoforged.neoforge.common.util.FakePlayer

class Debugger(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def useOn(ctx: UseOnContext): InteractionResult = {
    val world = ctx.getLevel
    if (ctx.getPlayer == null || ctx.getPlayer.isInstanceOf[FakePlayer]) return InteractionResult.FAIL

    world.getBlockEntity(ctx.getClickedPos) match {
      case host: SidedEnvironment =>
        if (!world.isClientSide) Debugger.reconnect(Array(host.sidedNode(ctx.getClickedFace)))
      case host: Environment =>
        if (!world.isClientSide) Debugger.reconnect(Array(host.node))
      case _ =>
        if (!world.isClientSide) Debugger.node.remove()
    }

    InteractionResult.sidedSuccess(world.isClientSide)
  }
}

object Debugger extends Environment {
  var node = api.Network.newNode(this, Visibility.Network).create()

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

  private def nodeInfo(node: Node) = s"{address = ${node.address}, reachability = ${node.reachability.name}" + (node match {
    case componentConnector: ComponentConnector => componentInfo(componentConnector) + connectorInfo(componentConnector)
    case component: Component => componentInfo(component)
    case connector: Connector => connectorInfo(connector)
    case _ =>
  }) + "}"

  private def componentInfo(component: Component) = s", type = component, name = ${component.name}, visibility = ${component.visibility.name}"

  private def connectorInfo(connector: Connector) = s", type = connector, buffer = ${connector.localBuffer}, bufferSize = ${connector.localBufferSize}"

  private def messageInfo(message: Message) = s"{name = ${message.name()}, source = ${nodeInfo(message.source)}, data = [${message.data.mkString(", ")}]}"
}
