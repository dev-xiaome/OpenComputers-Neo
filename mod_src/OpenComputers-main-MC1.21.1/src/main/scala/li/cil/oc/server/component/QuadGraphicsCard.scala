package li.cil.oc.server.component

import li.cil.oc.api.Network
import li.cil.oc.api.network.{Node, Visibility}
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.Tier
import li.cil.oc.common.datacomponents.{CompoundStorage, OCComponents}
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.DataComponentHolder
import net.neoforged.neoforge.common.MutableDataComponentHolder

class QuadGraphicsCard extends AbstractManagedEnvironment {
  private val HeadCount = 4
  private val VramScreensPerHead = 3.0

  private val graphicsCards = Array.fill(HeadCount) {
    new GraphicsCard(Tier.Two, Some(VramScreensPerHead), Visibility.Network)
  }

  setNode(Network.newNode(this, Visibility.Network).create())

  override def onConnect(connectedNode: Node): Unit = {
    if (connectedNode == node) {
      graphicsCards.foreach(graphicsCard => node.connect(graphicsCard.node))
    }
  }

  override def onDisconnect(disconnectedNode: Node): Unit = {
    if (disconnectedNode == node) {
      graphicsCards.foreach(graphicsCard => graphicsCard.node.remove())
    }
  }

  override def loadData(holder: DataComponentHolder): Unit = {
    super.loadData(holder)

    for (savedCards <- holder.getComponent(OCComponents.COMPONENT_NODES)) {
      savedCards.take(HeadCount).zip(graphicsCards).foreach {
        case (Some(storage), graphicsCard) => graphicsCard.loadData(storage)
        case _ =>
      }
    }
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super.saveData(holder)

    holder.setComponent(OCComponents.COMPONENT_NODES, graphicsCards.map { graphicsCard =>
      val storage = new CompoundStorage()
      graphicsCard.saveData(storage)
      Option(storage)
    }.toList)
  }
}
