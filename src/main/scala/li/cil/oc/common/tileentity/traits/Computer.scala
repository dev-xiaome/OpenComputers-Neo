package li.cil.oc.common.tileentity.traits

import java.lang
import java.util

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.client.Sound
import li.cil.oc.common.tileentity.RobotProxy
import li.cil.oc.integration.opencomputers.DriverRedstoneCard
import li.cil.oc.server.{PacketSender => ServerPacketSender}
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
 * （来自 [[ComponentInventory]]，1.21.1 里由 `components` 更名为 `componentEnvironments`）、
 * `setOutputEnabled` / `getOutput` /
 * `getBundledOutput` / `setRednetInput`（来自 [[BundledRedstoneAware]]）。
 *
 * ==集成层状态==
 *  - `API.machine` 已由 `common.Proxy`、`server.Proxy` 赋值为 `server.machine.Machine`，
 *    因此 `machine` 在服务端可用；代码中仍保留空值保护以兼容客户端（客户端 `machine` 恒为 null）。
 *  - 运行音循环（`client.Sound`）、运行状态同步（`server.PacketSender`）、红石卡判定
 *    （`integration.opencomputers.DriverRedstoneCard`）均已接回真实实现。
 *  - 机器人假玩家识别（`server.agent.Player`）**仍不可用**：该文件存在但未纳入
 *    `gradle.properties` 的 `scala_ported_packages` 编译白名单，见 [[isUseableByPlayer]]。
 *  - 仍存在的降级：`integration.util.Waila`（1.21.1 下由 Jade / TOP 走独立查询路径，不适用）、
 *    `integration.stargatetech2` 抽象总线卡（OCCE 亦已移除该集成，`hasAbstractBusCard` 恒 false）。
 */
trait Computer extends Environment with ComponentInventory with Rotatable with BundledRedstoneAware with AbstractBusAware with api.network.Analyzable with api.machine.MachineHost with StateAware {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: BlockEntity =>

  /**
   * 本宿主持有的机器实例。
   *
   * `api.Machine.create(this)` 由已赋值的 `API.machine`（见 `common.Proxy` / `server.Proxy`）
   * 提供实现，服务端可用；客户端恒为 null。所有使用点仍做空值保护，避免运行期 NPE。
   */
  private lazy val _machine: api.machine.Machine = if (isServer) api.Machine.create(this) else null

  def machine: api.machine.Machine = _machine

  // 客户端（machine 为 null）时节点不存在，这里返回 null 而不是抛 NPE。
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
      // 客户端播放 / 停止计算机运行音循环（对齐 OCCE traits.Computer#setRunning）。
      if (isClient) {
        runSound.foreach(sound =>
          if (_isRunning) Sound.startLoop(this, sound, 0.5f, (50 + world.random.nextInt(50)).toLong)
          else Sound.stopLoop(this))
      }
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
    // 1.21.1 的 `getStackInSlot` 返回 `ItemStack.EMPTY` 而不是 `null`，因此空槽位必须用
    // `isEmpty` 显式排除；否则空气堆叠也会被当成「内置组件」报给机器。
    case slot if !getStackInSlot(slot).isEmpty && isComponentSlot(slot, getStackInSlot(slot)) => getStackInSlot(slot)
  }.asJava

  /**
   * 已安装组件的环境列表。
   *
   * 注意：1.21.1 的 `BlockEntity` 有一个无参方法 `components()`（返回 `DataComponentMap`）。
   * 组件环境数组已改名为 `componentEnvironments` 以避免同名冲突；
   * 这里为保险起见仍用 `super[ComponentInventory]` 显式限定。
   */
  override def installedComponents: Iterable[ManagedEnvironment] =
    super[ComponentInventory].componentEnvironments.collect { case Some(component) => component }.toIndexedSeq

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
   * 是否安装了红石卡（对齐 OCCE traits.Computer#hasRedstoneCard）。
   *
   * `integration.opencomputers.DriverRedstoneCard` 与本项目其余集成层驱动一样已经就绪，
   * 这里直接使用其 `worksWith(stack, hostClass)` 判定；`machine` 为 null（例如客户端）时
   * 视为未运行，避免 NPE。
   */
  def hasRedstoneCard: Boolean = items.exists {
    case Some(item) if !item.isEmpty => machine != null && machine.isRunning && DriverRedstoneCard.worksWith(item, getClass)
    case _ => false
  }

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
    // 同步运行状态 / 错误指示灯给客户端（对齐 OCCE）。`server.PacketSender` 已就绪。
    ServerPacketSender.sendComputerState(this)
  }

  override def dispose(): Unit = {
    super.dispose()
    if (machine != null && !this.isInstanceOf[RobotProxy] && !moving) {
      machine.stop()
    }
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    // God, this is so ugly... will need to rework the robot architecture.
    // This is required for loading auxiliary data (kernel state), because the
    // coordinates in the actual robot won't be set properly, otherwise.
    // 机器人架构要求：加载内核状态前，先把机器人代理指向本方块实体的世界，
    // 否则 Robot 侧读到的坐标是错的。1.21.1 用公开的 `BlockEntity#setLevel`
    // （坐标由 Robot 自身的 `worldPosition` 在移动时同步，这里不再直接改写）。
    this match {
      case proxy: RobotProxy =>
        proxy.robot.setLevel(world)
      case _ =>
    }
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

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    if (machine != null) {
      // TODO(integration.util.Waila): 原实现在 `Waila.isSavingForTooltip` 时只写节点地址
      // （供工具提示显示），否则写完整内核状态。`integration.util.Waila` 未移植，
      // 且 1.21.1 的工具提示模组（Jade / TOP）走独立查询路径，这里固定写完整状态。
      nbt.setNewCompoundTag(Settings.namespace + "computer", machine.save)
    }
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    hasErrored = nbt.getBoolean("hasErrored")
    setRunning(nbt.getBoolean("isRunning"))
    _users.clear()
    _users ++= nbt.getList("users", Tag.TAG_STRING).map((tag: StringTag) => tag.getAsString)
    // 客户端读档后若机器已在运行，恢复运行音循环（对齐 OCCE）。
    if (_isRunning) runSound.foreach(sound => Sound.startLoop(this, sound, 0.5f, (1000 + world.random.nextInt(2000)).toLong))
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
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
  // 说明：OCCE 的 stillValid 会额外识别 `server.agent.Player`（机器人假玩家），
  // 并用其 `agent.ownerName()` 作为交互者名。本项目的 `server/agent/Player.scala`
  // 虽已存在，但尚未纳入 `gradle.properties` 的 `scala_ported_packages` 编译白名单
  // （该文件目前仍有大量未完成的 1.21.1 移植点），因此这里暂时统一取真实玩家名。
  // 白名单加入 `li/cil/oc/server/agent/**` 后，可按 OCCE 恢复该 match 分支。

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
