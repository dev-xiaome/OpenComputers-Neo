package li.cil.oc.common.inventory

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.{Item => ItemDriver}
import li.cil.oc.api.network
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.api.util.Lifecycle
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

// `getTag()` / `hasTag()` / `setTag()` 是补回 1.7.10 `ItemStack` NBT 访问的隐式扩展。
// Scala 2.13 不会把 `li.cil.oc` 包对象里的隐式类暴露给子包（见 `util.ExtendedItemStack`
// 的说明），因此这里必须显式引入。
import li.cil.oc.util.ItemStackNBTExtensions._

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 把物品栏里的组件物品通过 `li.cil.oc.api.Driver` 变成 `ManagedEnvironment` 并挂到节点上
 * （对应 1.7.10 的 `common.inventory.ComponentInventory`）。
 *
 * 1.21.1 迁移要点：
 *  - `getSizeInventory` → `getSlots`，`getInventoryStackLimit` → `getSlotLimit`。
 *  - `stack == null` 判空 → `stack == null || stack.isEmpty`（`getStackInSlot` 现在返回
 *    `ItemStack.EMPTY` 而不是 `null`）。
 *  - `tag.func_150296_c`（清空标签用）→ `tag.getAllKeys.asScala`。
 *  - `Item.dataTag(stack)` 属于尚未纳入编译范围的 `li.cil.oc.integration.opencomputers`
 *    包，见 [[dataTag]] 里的降级说明。
 */
trait ComponentInventory extends Inventory with network.Environment {
  private var _components: Array[Option[ManagedEnvironment]] = _
  protected var isSizeInventoryReady: Boolean = true

  /**
   * 槽位对应的组件环境（原名为 `components`）。
   *
   * 1.21.1 的 `BlockEntity` 有一个无参方法 `components()`（返回 `DataComponentMap`）。
   * 由于本 trait 混入方块实体，原来的 `def components`（空括号 vs 无括号）会被判定为
   * `inherits conflicting members`，无法在任何子类里消解。因此这里改名为 `componentEnvironments`。
   */
  def componentEnvironments: Array[Option[ManagedEnvironment]] = {
    if (_components == null && isSizeInventoryReady) {
      _components = Array.fill[Option[ManagedEnvironment]](getSlots)(None)
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
    for (slot <- 0 until getSlots if slot >= 0 && slot < componentEnvironments.length) {
      val stack = getStackInSlot(slot)
      if (stack != null && !stack.isEmpty && componentEnvironments(slot).isEmpty && isComponentSlot(slot, stack)) {
        componentEnvironments(slot) = Option(Driver.driverFor(stack)) match {
          case Some(driver) =>
            Option(driver.createEnvironment(stack, host)) match {
              case Some(component) =>
                applyLifecycleState(component, Lifecycle.LifecycleState.Constructing)
                try {
                  component.load(dataTag(driver, stack))
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
    componentEnvironments collect {
      case Some(component) =>
        applyLifecycleState(component, Lifecycle.LifecycleState.Initializing)
        connectItemNode(component.node)
        applyLifecycleState(component, Lifecycle.LifecycleState.Initialized)
    }
  }

  def disconnectComponents(): Unit = {
    componentEnvironments collect {
      case Some(component) =>
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposing)
        if (component.node != null) component.node.remove()
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposed)
    }
  }

  // ----------------------------------------------------------------------- //

  override def save(nbt: CompoundTag): Unit = {
    saveComponents()
    super.save(nbt) // Save items after updating their tags.
  }

  def saveComponents(): Unit = {
    for (slot <- 0 until getSlots) {
      val stack = getStackInSlot(slot)
      if (stack != null && !stack.isEmpty) {
        if (slot >= componentEnvironments.length) {
          // isSizeInventoryReady was added to resolve issues where an inventory was used before its
          // nbt data had been parsed. See https://github.com/MightyPirates/OpenComputers/issues/2522
          // If this error is hit again, perhaps another subtype needs to handle nbt loading like Case does
          OpenComputers.log.error(s"ComponentInventory componentEnvironments length ${componentEnvironments.length} does not accommodate inventory size ${getSlots}")
          return
        } else {
          componentEnvironments(slot) match {
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

  override def getSlotLimit(slot: Int): Int = 1

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = if (slot >= 0 && slot < componentEnvironments.length && isComponentSlot(slot, stack)) {
    Option(Driver.driverFor(stack)).foreach(driver =>
      Option(driver.createEnvironment(stack, host)) match {
        case Some(component) => this.synchronized {
          componentEnvironments(slot) = Some(component)
          applyLifecycleState(component, Lifecycle.LifecycleState.Constructing)
          try {
            component.load(dataTag(driver, stack))
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

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = if (slot >= 0 && slot < componentEnvironments.length) {
    // Uninstall component previously in that slot.
    componentEnvironments(slot) match {
      case Some(component) => this.synchronized {
        // Note to self: we have to remove the node from the network *before*
        // saving, to allow file systems to close their handles before they
        // are saved (otherwise hard drives would restore all handles after
        // being installed into a different computer, even!)
        componentEnvironments(slot) = None
        updatingComponents -= component
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposing)
        Option(component.node).foreach(_.remove())
        Option(Driver.driverFor(stack)).foreach(save(component, _, stack))
        // However, nodes then may add themselves to a network again, to
        // ensure they have an address that gets sent to the client, used
        // for associating some componentEnvironments with each other. So we do it again.
        // TODO Should be possible to avoid this with lifecycle state now.
        Option(component.node).foreach(_.remove())
        applyLifecycleState(component, Lifecycle.LifecycleState.Disposed)
      }
      case _ => // Nothing to do.
    }
  }

  def isComponentSlot(slot: Int, stack: ItemStack): Boolean = true

  protected def connectItemNode(node: Node): Unit = {
    if (this.node != null && node != null) {
      this.node.connect(node)
    }
  }

  /**
   * 取组件的数据标签（组件的存档都写在这里）。
   *
   * 原实现：`Option(driver.dataTag(stack)).getOrElse(Item.dataTag(stack))`，
   * 其中 `li.cil.oc.integration.opencomputers.Item.dataTag` 会把标签挂到
   * `<namespace>data` 下。该集成层尚未纳入编译范围，
   * TODO(integration.opencomputers.Item): 这里内联了同样的逻辑；等该包移植后改回调用。
   */
  protected def dataTag(driver: ItemDriver, stack: ItemStack): CompoundTag =
    Option(driver.dataTag(stack)).getOrElse(fallbackDataTag(stack))

  private def fallbackDataTag(stack: ItemStack): CompoundTag = {
    if (stack == null || stack.isEmpty) return new CompoundTag()
    if (!stack.hasTag()) stack.setTag(new CompoundTag())
    val nbt = stack.getTag()
    val key = Settings.namespace + "data"
    if (!nbt.contains(key)) nbt.put(key, new CompoundTag())
    nbt.getCompound(key)
  }

  protected def save(component: ManagedEnvironment, driver: ItemDriver, stack: ItemStack): Unit = {
    try {
      val tag = dataTag(driver, stack)
      // Clear the tag compound before saving to get the same behavior as
      // in tile entities (otherwise entries have to be cleared manually).
      //
      // 必须先用 `toSeq` 复制一份 key：1.21.1 的 `CompoundTag#getAllKeys` 返回的是
      // 内部 `HashMap` 的 **keySet 视图**（1.7.10 的 `func_150296_c` 也是，但当时的
      // 遍历写法侥幸没触发），边遍历边 `remove` 会抛 `ConcurrentModificationException`，
      // 结果是组件数据在保存时被整份丢弃（日志里表现为
      // "An item component of type '...' threw an error while saving"）。
      for (key <- tag.getAllKeys.asScala.toSeq) {
        tag.remove(key)
      }
      component.save(tag)
    } catch {
      case e: Throwable => OpenComputers.log.warn(s"An item component of type '${component.getClass.getName}' (provided by driver '${driver.getClass.getName}') threw an error while saving.", e)
    }
  }

  protected def applyLifecycleState(component: AnyRef, state: Lifecycle.LifecycleState): Unit = component match {
    case lifecycle: Lifecycle => lifecycle.onLifecycleStateChange(state)
    case _ =>
  }
}
