package li.cil.oc.common.tileentity.traits

import java.lang
import java.util

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.common.tileentity.RobotProxy
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 计算机方块实体 trait（对应 1.7.10 的 `traits.Computer`）：机箱 / 微控制器 / 机器人
 * 代理 / 服务器等所有"跑机器"的方块实体都混入它。
 *
 * ==1.21.1 迁移要点==
 *  - `updateEntity()` → [[TileEntity#tick]]；`validate()` → [[TileEntity#initialize]]；
 *    `invalidate()` / `onChunkUnload()` → [[TileEntity#dispose]]。
 *  - `world.markBlockForUpdate(x, y, z)` → [[TileEntity#markBlockForUpdate]]。
 *  - `NBTTagCompound` 的 `hasKey/setBoolean/setString/getBoolean` → `contains/putBoolean/
 *    putString/getBoolean`；`new NBTTagString(s)` → `StringTag.valueOf(s)`；
 *    `NBT.TAG_STRING` → `Tag.TAG_STRING`。
 *  - `player.getCommandSenderName` → `player.getGameProfile.getName`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）；客户端专用逻辑用注释标注。
 *
 * ==已就绪的 API 表面（Lua 侧 / GUI / 分析器依赖，不要改名）==
 * `machine`、`node`、`isRunning` / `setRunning`、`hasErrored`、`canInteract`、
 * `internalComponents`、`installedComponents`、`hasRedstoneCard`、`hasAbstractBusCard`、
 * `componentSlot`（由具体方块实现）、`connectComponents` / `disconnectComponents`
 * （来自 [[ComponentInventory]]）、`components`、`setOutputEnabled` / `getOutput` /
 * `getBundledOutput` / `setRednetInput`（来自 [[BundledRedstoneAware]]）。
 *
 * ==降级清单==
 *  - `api.Machine.create(this)`：`API.machine` 在 `server.machine` 移植前为 `null`，
 *    因此 `machine` 在服务端也会是 `null`；所有用到 `machine` 的地方都做了空值保护，
 *    并留下 `TODO(server.machine)`。
 *  - `client.Sound`（运行音循环）、`server.PacketSender`（状态同步）、
 *    `server.agent.Player`（机器人假玩家）、`integration.util.Waila`、
 *    `integration.opencomputers.DriverRedstoneCard`、`integration.stargatetech2.*`
 *    均未移植，逐处标注 `TODO(...)`。
 */
trait Computer extends Environment with ComponentInventory with Rotatable with BundledRedstoneAware with AbstractBusAware with api.network.Analyzable with api.machine.MachineHost with StateAware {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: BlockEntity =>

  /**
   * 本宿主持有的机器实例。
   *
   * TODO(server.machine): 原实现是 `api.Machine.create(this)`，由 `li.cil.oc.server.machine.Machine`
   * 提供实现（`API.machine` 在 mod 初始化时被赋值）。`server.machine` 尚未移植，因此
   * `API.machine == null`，`api.Machine.create` 会返回 `null`（客户端侧本来就返回 `null`）。
   * 机器层移植完成后**本文件无需改动**，只需保证 `API.machine` 被赋值；在那之前所有
   * 使用点都做了空值保护，避免运行期 NPE。
   */
  private lazy val _machine: api.machine.Machine = if (isServer) api.Machine.create(this) else null

  def machine: api.machine.Machine = _machine

  // TODO(server.machine): 机器为 null 时（见上）节点也不存在，这里返回 null 而不是抛 NPE。
  override def node: Node = if (isServer && machine != null) machine.node else null

  private var _isRunning = false

  // For client side rendering of error LED indicator.
  var hasErrored = false

  private val _users = mutable.Set.empty[String]

  protected def runSound = Option("computer_running")

  // ----------------------------------------------------------------------- //

  def canInteract(player: String) =
    if (isServer && machine != null) machine.canInteract(player)
    else !Settings.get.canComputersBeOwned || _users.isEmpty || _users.contains(player)

  def isRunning: Boolean = _isRunning

  def setRunning(value: Boolean): Unit = if (value != _isRunning) {
    _isRunning = value
    if (value) {
      hasErrored = false
    }
    if (world != null) {
      // 原为 `world.markBlockForUpdate(x, y, z)`。
      markBlockForUpdate()
      // TODO(client.Sound): 客户端原本在此调用 `client.Sound.startLoop(this, runSound, ...)` /
      // `client.Sound.stopLoop(this)` 播放计算机运行音循环。`li.cil.oc.client` 尚未移植，
      // 该逻辑留待客户端音效层接入（`runSound` 供其使用）。
    }
  }

  /** 仅客户端使用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。 */
  def setUsers(list: Iterable[String]): Unit = {
    _users.clear()
    _users ++= list
  }

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    if (isRunning) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //

  override def internalComponents(): lang.Iterable[ItemStack] = (0 until getSlots).collect {
    case slot if getStackInSlot(slot) != null && isComponentSlot(slot, getStackInSlot(slot)) => getStackInSlot(slot)
  }.asJava

  /**
   * 已安装组件的环境列表。
   *
   * 注意：1.21.1 的 `BlockEntity` 有一个同名的 `components()` 方法（返回 `DataComponentMap`），
   * 它会遮蔽混合进来的 [[ComponentInventory#components]]，因此必须用 `super[...]` 显式限定。
   */
  override def installedComponents: Iterable[ManagedEnvironment] =
    super[ComponentInventory].components.collect { case Some(component) => component }.toIndexedSeq

  override def onMachineConnect(node: api.network.Node): Unit = this.onConnect(node)

  override def onMachineDisconnect(node: api.network.Node): Unit = this.onDisconnect(node)

  /**
   * 是否安装了抽象总线卡。
   *
   * TODO(integration.opencomputers): 原实现为
   * `items.exists { case Some(item) => machine.isRunning && DriverAbstractBusCard.worksWith(item, getClass) ... }`。
   * `integration` 包（含 StargateTech2 的抽象总线卡驱动）未纳入编译范围，这里暂时恒为 false。
   */
  def hasAbstractBusCard = false

  /**
   * 是否安装了红石卡。
   *
   * TODO(integration.opencomputers): 原实现为
   * `items.exists { case Some(item) => machine.isRunning && DriverRedstoneCard.worksWith(item, getClass) ... }`。
   * `integration` 包（含红石卡驱动）未纳入编译范围，这里暂时恒为 false，
   * 因此计算机目前不会输出红石信号；集成层移植后请恢复原判定。
   */
  def hasRedstoneCard = false

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    // If we're not yet in a network we might have just been loaded from disk,
    // meaning there may be other tile entities that also have not re-joined
    // the network. We skip the update this round to allow other tile entities
    // to join the network, too, avoiding issues of missing nodes (e.g. in the
    // GPU which would otherwise loose track of its screen).
    // `isConnected` 已经隐含 `machine != null`（node 来自 machine），这里显式写出以免运行期 NPE。
    if (isServer && isConnected && machine != null) {
      updateComputer()

      val running = machine.isRunning
      val errored = machine.lastError != null
      if (_isRunning != running || hasErrored != errored) {
        _isRunning = running
        hasErrored = errored
        onRunningChanged()
      }

      updateComponents()
    }

    super.tick()
  }

  protected def updateComputer(): Unit = {
    if (machine != null) machine.update()
  }

  protected def onRunningChanged(): Unit = {
    markDirty()
    // TODO(server.PacketSender): 原为 ServerPacketSender.sendComputerState(this) 同步运行状态/错误灯。
    // 网络层移植后改为发送 ComputerState 包；这里退化为方块更新。
    markBlockForUpdate()
  }

  override def dispose(): Unit = {
    super.dispose()
    if (machine != null && !this.isInstanceOf[RobotProxy] && !moving) {
      machine.stop()
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    // God, this is so ugly... will need to rework the robot architecture.
    // This is required for loading auxiliary data (kernel state), because the
    // coordinates in the actual robot won't be set properly, otherwise.
    // TODO(server.machine): 原实现在这里把本方块实体的坐标写进内部 `RobotProxy.robot`
    // （`proxy.robot.xCoord = xCoord` 等）以便加载机器内核状态。1.21.1 的 `BlockEntity`
    // 位置由 `worldPosition` 统一提供、不再有可写的 xCoord/yCoord/zCoord；
    // 待 Robot / RobotProxy 移植完成后，请改为调用 Robot 暴露的显式坐标同步方法。
    if (machine != null) {
      machine.load(nbt.getCompound(Settings.namespace + "computer"))
    }

    // Kickstart initialization to avoid values getting overwritten by
    // readFromNBTForClient if that packet is handled after a manual
    // initialization / state change packet.
    if (machine != null) {
      setRunning(machine.isRunning)
    }
    _isOutputEnabled = hasRedstoneCard
    _isAbstractBusAvailable = hasAbstractBusCard
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    if (machine != null) {
      // TODO(integration.util.Waila): 原实现在 `Waila.isSavingForTooltip` 时只写节点地址
      // （供工具提示显示），否则写完整内核状态。`integration.util.Waila` 未移植，
      // 且 1.21.1 的工具提示模组（Jade / TOP）走独立查询路径，这里固定写完整状态。
      nbt.setNewCompoundTag(Settings.namespace + "computer", machine.save)
    }
  }

  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    hasErrored = nbt.getBoolean("hasErrored")
    setRunning(nbt.getBoolean("isRunning"))
    _users.clear()
    _users ++= nbt.getList("users", Tag.TAG_STRING).map((tag: StringTag) => tag.getAsString)
    // TODO(client.Sound): 原实现在 `_isRunning` 为真时调用 `client.Sound.startLoop(...)`。
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putBoolean("hasErrored", machine != null && machine.lastError != null)
    nbt.putBoolean("isRunning", isRunning)
    if (machine != null) {
      nbt.setNewTagList("users", machine.users.map(user => StringTag.valueOf(user)).toIndexedSeq)
    }
  }

  // ----------------------------------------------------------------------- //

  override def markDirty(): Unit = {
    super.markDirty()
    if (isServer) {
      if (machine != null) machine.onHostChanged()
      setOutputEnabled(hasRedstoneCard)
      isAbstractBusAvailable = hasAbstractBusCard
    }
  }

  override def isUseableByPlayer(player: Player): Boolean =
    super.isUseableByPlayer(player) && canInteract(player.getGameProfile.getName)
  // TODO(server.agent): 原实现在此识别 `server.agent.Player`（机器人使用的假玩家），
  // 并用其 `agent.ownerName()` 作为交互者名字。`server.agent` 未移植，这里统一取真实玩家名。

  override protected def onRotationChanged(): Unit = {
    super.onRotationChanged()
    checkRedstoneInputChanged()
  }

  override protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    val toLocalArgs = RedstoneChangedEventArgs(toLocal(args.side), args.oldValue, args.newValue, args.color)
    if (machine != null) machine.node.sendToNeighbors("redstone.changed", toLocalArgs)
  }

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] =
    Array(if (machine != null) machine.node else null)
}
