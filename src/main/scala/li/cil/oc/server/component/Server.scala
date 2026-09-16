package li.cil.oc.server.component

import java.lang.Iterable
import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api
import li.cil.oc.api.Machine
import li.cil.oc.api.component.RackBusConnectable
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.internal
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.container.MenuOpening
import li.cil.oc.common.inventory.ComponentInventory
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.network.Connector
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

import scala.jdk.CollectionConverters._

class Server(val rack: api.internal.Rack, val slot: Int) extends Environment with MachineHost with ServerInventory with ComponentInventory with Analyzable with internal.Server with DeviceInfo {
  lazy val machine = Machine.create(this)

  // 1.21.1：`Level#isRemote` → `Level#isClientSide`。
  val node = if (!rack.world.isClientSide) machine.node else null

  var wasRunning = false
  var hadErrored = false
  var lastFileSystemAccess = 0L
  var lastNetworkActivity = 0L

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.System,
    DeviceAttribute.Description -> "Server",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Blader",
    // 1.21.1：`IInventory#getSizeInventory` → `IItemHandler#getSlots`。
    DeviceAttribute.Capacity -> getSlots.toString
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //
  // Environment

  override def onConnect(node: Node): Unit = {
    if (node == this.node) {
      connectComponents()
    }
  }

  override def onDisconnect(node: Node): Unit = {
    if (node == this.node) {
      disconnectComponents()
    }
  }

  override def onMessage(message: Message): Unit = {
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    if (!rack.world.isClientSide) {
      machine.load(nbt.getCompound("machine"))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    if (!rack.world.isClientSide) {
      nbt.setNewCompoundTag("machine", machine.save)
    }
  }

  // ----------------------------------------------------------------------- //
  // MachineHost

  /**
   * 已安装的组件物品。
   *
   * 1.21.1：`getSizeInventory` → `getSlots`，空槽返回 `ItemStack.EMPTY`（不再判 `null`），
   * 并且 `MachineHost#internalComponents` 要求返回 `java.lang.Iterable`，
   * 因此这里把 Scala 序列显式转成 Java 集合。
   */
  override def internalComponents(): Iterable[ItemStack] = {
    val stacks = (0 until getSlots).collect {
      case i if !getStackInSlot(i).isEmpty && isComponentSlot(i, getStackInSlot(i)) => getStackInSlot(i)
    }
    scala.jdk.javaapi.CollectionConverters.asJavaCollection(stacks)
  }

  override def componentSlot(address: String) = componentEnvironments.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  override def onMachineConnect(node: Node) = onConnect(node)

  override def onMachineDisconnect(node: Node) = onDisconnect(node)

  // ----------------------------------------------------------------------- //
  // EnvironmentHost

  override def xPosition = rack.xPosition

  override def yPosition = rack.yPosition

  override def zPosition = rack.zPosition

  override def world = rack.world

  override def markChanged() = rack.markChanged()

  // ----------------------------------------------------------------------- //
  // ServerInventory

  override def tier = Delegator.subItem(container) match {
    case Some(server: item.Server) => server.tier
    case _ => 0
  }

  /**
   * 玩家是否还能操作这台服务器。
   *
   * 1.21.1：`api.internal.Rack` 接口本身不再有 `isUseableByPlayer`（它只继承
   * `IItemHandler`），实现类 [[li.cil.oc.common.tileentity.Rack]] 通过
   * `tileentity.traits.Inventory` 仍然提供该方法。因此这里先尝试按实现类判定，
   * 失败时退化为原版 64 格距离判定（与 `traits.Inventory#isUseableByPlayer` 一致）。
   */
  override def isUseableByPlayer(player: Player): Boolean = rack match {
    case inventory: li.cil.oc.common.tileentity.Rack => inventory.isUseableByPlayer(player)
    case _ =>
      // TODO(server): `Rack` 的其它实现（若有）没有可用性查询，这里退化为 8 格距离判定；
      // 只影响「玩家能否打开服务器界面 / 启动服务器」，不影响机器运行。
      player.distanceToSqr(rack.xPosition, rack.yPosition, rack.zPosition) <= 64
  }

  // ----------------------------------------------------------------------- //
  // ItemStackInventory

  override def host = rack

  // ----------------------------------------------------------------------- //
  // ComponentInventory

  override def container = rack.getStackInSlot(slot)

  override protected def connectItemNode(node: Node): Unit = {
    if (node != null) {
      api.Network.joinNewNetwork(machine.node)
      machine.node.connect(node)
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (!rack.world.isClientSide) {
      val slotType = InventorySlots.server(tier)(slot).slot
      if (slotType == Slot.CPU) {
        machine.stop()
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // RackMountable

  override def getData: CompoundTag = {
    val nbt = new CompoundTag()
    nbt.putBoolean("isRunning", wasRunning)
    nbt.putBoolean("hasErrored", hadErrored)
    nbt.putLong("lastFileSystemAccess", lastFileSystemAccess)
    nbt.putLong("lastNetworkActivity", lastNetworkActivity)
    nbt
  }

  override def getConnectableCount: Int = componentEnvironments.count {
    case Some(_: RackBusConnectable) => true
    case _ => false
  }

  override def getConnectableAt(index: Int): RackBusConnectable = componentEnvironments.collect {
    case Some(busConnectable: RackBusConnectable) => busConnectable
  }.apply(index)

  override def onActivate(player: Player, hitX: Float, hitY: Float): Boolean = {
    // 1.21.1：`player.getEntityWorld` → `player.level()`；`player.isSneaking` → `player.isShiftKeyDown`。
    if (!player.level().isClientSide) {
      if (player.isShiftKeyDown) {
        if (!machine.isRunning && isUseableByPlayer(player)) {
          wasRunning = false
          hadErrored = false
          machine.start()
        }
      }
      else {
        // `player.openGui(OpenComputers, GuiType.ServerInRack.id, world, x, embedSlot(y, slot), z)`（1.7.10）
        // 在 1.21.1 已移除。服务端改为 `player.openMenu(MenuProvider)` 建立与
        // `GuiHandler#getServerMenu` 中 `ServerInRack` 分支相同的容器。
        // TODO(client): 客户端界面仍由 `MenuType` 的 Screen 工厂重建，`client` 层移植前
        // 只会打开容器、没有可见界面（不影响服务器机器本身的运行）。
        // 1.7.10 把机架坐标与插槽压进 `y`（`GuiType.embedSlot`）；1.21.1 改为把
        // 宿主坐标 + 插槽写进菜单载荷，因此改用 `MenuOpening.openRackSlot`。
        // 容器侧还需要「哪台机架 + 第几格」（`isInRack` / `rack` / `rackSlot`），
        // 与 [[li.cil.oc.common.container.MenuTypes]] 中 `Server` 的机架分支保持一致。
        val rackPos = rack match {
          case blockEntity: net.minecraft.world.level.block.entity.BlockEntity => blockEntity.getBlockPos
          case _ => new net.minecraft.core.BlockPos(
            math.floor(rack.xPosition).toInt, math.floor(rack.yPosition).toInt, math.floor(rack.zPosition).toInt)
        }
        val rackBlockEntity = rack match {
          case blockEntity: li.cil.oc.common.tileentity.Rack => Option(blockEntity)
          case _ => None
        }
        MenuOpening.openRackSlot(player, rackPos, slot, Component.empty())((windowId, playerInventory) =>
          new li.cil.oc.common.container.Server(
            windowId, playerInventory, this,
            isInRack = true, isRunningProvider = () => machine.isRunning,
            rack = rackBlockEntity, rackSlot = slot))
      }
    }
    true
  }

  // ----------------------------------------------------------------------- //
  // ManagedEnvironment

  override def canUpdate: Boolean = true

  override def update(): Unit = {
    if (!rack.world.isClientSide) {
      machine.update()

      val isRunning = machine.isRunning
      val hasErrored = machine.lastError != null
      if (isRunning != wasRunning || hasErrored != hadErrored) {
        rack.markChanged(slot)
      }
      wasRunning = isRunning
      hadErrored = hasErrored
      if (tier == Tier.Four) node.asInstanceOf[Connector].changeBuffer(Double.PositiveInfinity)
    }

    updateComponents()
  }

  // ----------------------------------------------------------------------- //
  // StateAware

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    if (machine.isRunning) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //
  // Analyzable

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float) = Array(machine.node)
}
