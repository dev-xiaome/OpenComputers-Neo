package li.cil.oc.server.driver

import java.nio.charset.Charset
import com.google.common.hash.Hashing
import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.api.network._
import li.cil.oc.common.datacomponents.{CompoundStorage, OCComponents}
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

class CompoundBlockEnvironment(val name: String, val environments: (String, ManagedEnvironment)*) extends ManagedEnvironment {
  // Block drivers with visibility < network usually won't make much sense,
  // but let's play it safe and use the least possible visibility based on
  // the drivers we encapsulate.
  val node: Component = api.Network.newNode(this, (environments.filter(_._2.node != null).map(_._2.node.reachability) ++ Seq(Visibility.None)).maxBy(_.ordinal())).
    withComponent(name).
    create()

  val updatingEnvironments: Seq[ManagedEnvironment] = environments.map(_._2).filter(_.canUpdate)

  // Force all wrapped components to be neighbor visible, since we as their
  // only neighbor will take care of all component-related interaction.
  for ((_, environment) <- environments) environment.node match {
    case component: Component => component.setVisibility(Visibility.Neighbors)
    case _ =>
  }

  override def canUpdate: Boolean = environments.exists(_._2.canUpdate)

  override def update(): Unit = {
    for (environment <- updatingEnvironments) {
      environment.update()
    }
  }

  override def onMessage(message: Message): Unit = {}

  override def onConnect(node: Node): Unit = {
    if (node == this.node) {
      for ((_, environment) <- environments if environment.node != null) {
        node.connect(environment.node)
      }
    }
  }

  override def onDisconnect(node: Node): Unit = {
    if (node == this.node) {
      for ((_, environment) <- environments if environment.node != null) {
        environment.node.remove()
      }
    }
  }

  private final val TypeHashTag = "typeHash"

  override def loadData(holder: DataComponentHolder): Unit = {
    for(saveTypeHash -> storage <- holder.getComponent(OCComponents.COMPOUND_DRIVER)) {
      if(saveTypeHash != typeHash) return
      node.loadData(holder)

      for ((driver, environment) <- environments) {
        if (storage.contains(driver)) {
          try {
            environment.loadData(storage(driver))
          } catch {
            case e: Throwable => OpenComputers.log.warn(s"A block component of type '${environment.getClass.getName}' (provided by driver '$driver') threw an error while loading.", e)
          }
        }
      }
    }
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    node.saveData(holder)
    holder.setComponent(OCComponents.COMPOUND_DRIVER, typeHash -> Map.from(environments.map {
      case (driver, environment) =>
        val storage = new CompoundStorage()
        environment.saveData(storage)
        driver -> storage
    }))
  }

  private def typeHash = {
    val hash = Hashing.sha256().newHasher()
    environments.map(_._2.getClass.getName).sorted.foreach(hash.putString(_, Charset.defaultCharset()))
    hash.hash().asLong()
  }
}
