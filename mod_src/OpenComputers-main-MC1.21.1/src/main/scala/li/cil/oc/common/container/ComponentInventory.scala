package li.cil.oc.common.container

import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.api.{Driver, ImmutableItemStack, network}
import li.cil.oc.api.driver.{DriverItem => ItemDriver}
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.{Component, ManagedEnvironment, Node, Visibility}
import li.cil.oc.api.util.Lifecycle
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.integration.opencomputers.{DriverTablet, Item}
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.mutable

trait ComponentInventory extends Inventory with network.Environment {
  private var _components: Array[Option[ManagedEnvironment]] = _
  protected var isSizeInventoryReady: Boolean = true

  // renamed as to not conflict with Minecraft's components() method on some classes
  def componentSlots: Array[Option[ManagedEnvironment]] = {
    if (_components == null && isSizeInventoryReady) {
      _components = Array.fill[Option[ManagedEnvironment]](getContainerSize)(None)
    }
    if (_components == null) Array[Option[ManagedEnvironment]]() else _components
  }

  protected val updatingComponents = mutable.ArrayBuffer.empty[ManagedEnvironment]

  // ----------------------------------------------------------------------- //

  def host: EnvironmentHost

  // ----------------------------------------------------------------------- //

  def updateComponents(): Unit = {
    if (updatingComponents.nonEmpty) {
      var i = 0
      // ArrayBuffer.foreach caches the size for performance reasons, but that
      // will cause issues if the list changed during iteration (e.g. because
      // a component removed itself / another component, such as the self-
      // destruct card from Computronics). Also, this list will generally be
      // quite short, so it won't have any noticeable impact, anyway.
      while (i < updatingComponents.size) {
        updatingComponents(i).update()
        i += 1
      }
    }
  }

  // ----------------------------------------------------------------------- //

  def connectComponents(): Unit = {
    // Managed component environments are allowed to query their host's world
    // during construction/load (e.g. TextBuffer, Trading Upgrade). Block
    // entities are deserialized before Minecraft attaches their Level, so
    // defer component construction until the host is actually live.
    if (host.getEnvironmentLevel == null) return

    for (slot <- 0 until getContainerSize if slot >= 0 && slot < componentSlots.length) {
      val stack = getItem(slot)
      if (!stack.isEmpty && componentSlots(slot).isEmpty && isComponentSlot(slot, stack)) {
        componentSlots(slot) = Option(Driver.driverFor(stack)) match {
          case Some(driver) =>
            Option(driver.createEnvironment(stack, host)) match {
              case Some(component) =>
                applyLifecycleState(component, Lifecycle.LifecycleState.Constructing)
                try {
                  // Restore node identity first. Old OC treated oc:node as part
                  // of the component inventory contract; in the Data Component
                  // port the equivalent is OCComponents.ADDRESS on the stack.
                  // Do this explicitly so a component override cannot
                  // accidentally regenerate its address by omitting super.
                  load(component, driver, stack)
                }
                catch {
                  case e: Throwable => OpenComputers.log.warn(s"An item component of type '${component.getClass.getName}' (provided by driver '${driver.getClass.getName}') threw an error while loading.", e)
                }
                if (component.canUpdate) {
                  assert(!updatingComponents.contains(component))
                  updatingComponents += component
                }
                Some(component)
              case _ => None
            }
          case _ => None
        }
      }
    }
    // Make sure our node is connected.
    api.Network.joinNewNetwork(node)
    componentSlots collect {
      case Some(component) =>
        applyLifecycleState(component, Lifecycle.LifecycleState.Initializing)
        connectItemNode(component.node)
        applyLifecycleState(component, Lifecycle.LifecycleState.Initialized)
    }
  }

  def disconnectComponents(): Unit = {
    componentSlots collect {
      case Some(component) =>
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposing)
        if (component.node != null) component.node.remove()
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposed)
    }
  }

  // ----------------------------------------------------------------------- //

  override def component: DataComponentType[List[ImmutableItemStack]] =
    OCComponents.COMPONENTS.get()

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    saveComponents()
    super.saveData(holder)
  }

  def saveComponents(): Unit = {
    for (slot <- 0 until getContainerSize) {
      val stack = getItem(slot)
      if (!stack.isEmpty) {
        if (slot >= componentSlots.length) {
          // isSizeInventoryReady was added to resolve issues where an inventory was used before its
          // nbt data had been parsed. See https://github.com/MightyPirates/OpenComputers/issues/2522
          // If this error is hit again, perhaps another subtype needs to handle nbt loading like Case does
          OpenComputers.log.error(s"ComponentInventory components length ${componentSlots.length} does not accommodate inventory size ${getContainerSize}")
          return
        } else {
          componentSlots(slot) match {
            case Some(component) =>
              // We're guaranteed to have a driver for entries.
              save(component, Driver.driverFor(stack), stack)
            case _ => // Nothing special to save.
          }
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def getMaxStackSize = 1

  override protected def onItemAdded(slot: Int, stack: ItemStack) = if (slot >= 0 && slot < componentSlots.length && isComponentSlot(slot, stack)) {
    Option(Driver.driverFor(stack)).foreach(driver =>
      Option(driver.createEnvironment(stack, host)) match {
        case Some(component) => this.synchronized {
          componentSlots(slot) = Some(component)
          applyLifecycleState(component, Lifecycle.LifecycleState.Constructing)
          try {
            load(component, driver, stack)
          } catch {
            case e: Throwable => OpenComputers.log.warn(s"An item component of type '${component.getClass.getName}' (provided by driver '${driver.getClass.getName}') threw an error while loading.", e)
          }
          if (component.canUpdate) {
            assert(!updatingComponents.contains(component))
            updatingComponents += component
          }
          applyLifecycleState(component, Lifecycle.LifecycleState.Initializing)
          connectItemNode(component.node)
          applyLifecycleState(component, Lifecycle.LifecycleState.Initialized)
          save(component, driver, stack)
        }
        case _ => // No environment (e.g. RAM).
      })
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = if (slot >= 0 && slot < componentSlots.length) {
    // Uninstall component previously in that slot.
    componentSlots(slot) match {
      case Some(component) => this.synchronized {
        // Note to self: we have to remove the node from the network *before*
        // saving, to allow file systems to close their handles before they
        // are saved (otherwise hard drives would restore all handles after
        // being installed into a different computer, even!)
        componentSlots(slot) = None
        updatingComponents -= component
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposing)
        Option(component.node).foreach(_.remove())
        Option(Driver.driverFor(stack)).foreach(save(component, _, stack))
        // However, nodes then may add themselves to a network again, to
        // ensure they have an address that gets sent to the client, used
        // for associating some components with each other. So we do it again.
        // TODO Should be possible to avoid this with lifecycle state now.
        Option(component.node).foreach(_.remove())
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposed)
      }
      case _ => // Nothing to do.
    }
  }

  def isComponentSlot(slot: Int, stack: ItemStack) = true

  protected def connectItemNode(node: Node): Unit = {
    if (this.node != null && node != null) {
      this.node.connect(node)
    }
  }

  protected def load(component: ManagedEnvironment, driver: ItemDriver, stack: ItemStack): Unit = {
    def loadFrom(persistenceStack: ItemStack): Unit = {
      // Restore node identity first. Old OC treated oc:node as part of the
      // component inventory contract; in the Data Component port the
      // equivalent is OCComponents.ADDRESS on the persistence stack.
      if (component.node != null) component.node.loadData(persistenceStack)
      component.loadData(persistenceStack)
    }

    driver match {
      case DriverTablet =>
        // A tablet exposes its embedded filesystem as the component. Loading
        // from the outer tablet stack would make the filesystem consume the
        // tablet's own data components (including its battery state).
        DriverTablet.withFileSystemStack(stack)(loadFrom)
        // The embedded drive is normally a neighbor-visible component inside
        // the tablet. In a charger it is a proxy component for the charger's
        // whole network, so restore the driver's visibility override after
        // loading the drive's persisted component state.
        component.node match {
          case node: Component => node.setVisibility(Visibility.Network)
          case _ =>
        }
      case _ => loadFrom(stack)
    }
  }

  protected def save(component: ManagedEnvironment, driver: ItemDriver, stack: ItemStack): Unit = {
    try {
      def saveTo(persistenceStack: ItemStack): Unit = {
        driver match {
          case _: api.driver.item.Memory =>
            // RAM has no persistent per-item state. Its environment gets a temporary
            // network node while installed, but persisting that node address makes
            // otherwise identical RAM sticks differ as ItemStacks and prevents them
            // from stacking again after use. Also clean up addresses written by older
            // builds of the Data Component port.
            persistenceStack.remove(OCComponents.ADDRESS.get())
            persistenceStack.remove(OCComponents.VISIBILITY.get())

          case _ =>
            component.saveData(persistenceStack)

            // Enforce node persistence at the inventory boundary. This is the modern
            // equivalent of old OC's per-component oc:node tag: if an environment
            // forgot to call super.saveData(), its address must still survive.
            if (component.node != null) {
              component.node.saveData(persistenceStack)

              val persisted = persistenceStack.get(OCComponents.ADDRESS)
              if (component.node.address != null &&
                  (persisted == null || persisted != component.node.address)) {
                OpenComputers.log.error(
                  s"Failed to persist component node address for ${component.getClass.getName}: " +
                    s"node=${component.node.address}, stack=$persisted")
              }
            }
        }
      }

      driver match {
        case DriverTablet =>
          // The environment represented by a tablet in a component slot is its
          // embedded filesystem. Persist it into that nested disk ItemStack and
          // then write the updated contents back to the tablet. Never serialize
          // the filesystem node directly onto the outer tablet stack, because
          // connector persistence uses OCComponents.CHARGE and would overwrite
          // the tablet's battery.
          DriverTablet.withFileSystemStack(stack)(saveTo)
        case _ => saveTo(stack)
      }
    } catch {
      case e: Throwable => OpenComputers.log.warn(s"An item component of type '${component.getClass.getName}' (provided by driver '${driver.getClass.getName}') threw an error while saving.", e)
    }
  }

  protected def applyLifecycleState(component: AnyRef, state: Lifecycle.LifecycleState): Unit = component match {
    case lifecycle: Lifecycle => lifecycle.onLifecycleStateChange(state)
    case _ =>
  }
}
