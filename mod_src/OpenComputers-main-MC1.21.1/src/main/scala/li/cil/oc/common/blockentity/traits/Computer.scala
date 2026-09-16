package li.cil.oc.common.blockentity.traits

import java.lang
import java.util
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Persistable
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.Node
import li.cil.oc.client.Sound
import li.cil.oc.common.blockentity.RobotProxy
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.integration.opencomputers.DriverRedstoneCard
import li.cil.oc.server.agent
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.{DataComponentHolder, DataComponentMap}
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag
import net.minecraft.core.{Direction, HolderLookup}
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

import scala.collection.convert.ImplicitConversionsToJava._
import scala.collection.mutable
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.common.MutableDataComponentHolder

import scala.jdk.CollectionConverters._

trait Computer extends Environment with ComponentInventory with Rotatable with BundledRedstoneAware with api.network.Analyzable with api.machine.MachineHost with StateAware with Tickable {
  private lazy val _machine = if (isServer) api.Machine.create(this) else null

  def machine: Machine = _machine

  override def node: Node = if (isServer) machine.node else null

  private var _isRunning = false
  private var pendingMachineData: DataComponentMap = null

  // For client side rendering of error LED indicator.
  var hasErrored = false

  private val _users = mutable.Set.empty[String]

  protected def runSound = Option("computer_running")

  // ----------------------------------------------------------------------- //

  def canInteract(player: String): Boolean =
    if (isServer) machine.canInteract(player)
    else !Settings.get.canComputersBeOwned || _users.isEmpty || _users.contains(player)

  def isRunning: Boolean = _isRunning

  def setRunning(value: Boolean): Unit = if (value != _isRunning) {
    _isRunning = value
    if (value) {
      hasErrored = false
    }
    if (getLevel != null && !isMoving) {
      getLevel.sendBlockUpdated(getBlockPos, getLevel.getBlockState(getBlockPos), getLevel.getBlockState(getBlockPos), 3)
      if (getLevel.isClientSide) {
        runSound.foreach(sound =>
          if (_isRunning) Sound.startLoop(this, sound, 0.5f, (50 + getLevel.random.nextInt(50)).toLong)
          else Sound.stopLoop(this)
        )
      }
    }
  }

  @OnlyIn(Dist.CLIENT)
  def setUsers(list: Iterable[String]): Unit = {
    _users.clear()
    _users ++= list
  }

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    if (isRunning) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //

  override def internalComponents(): java.lang.Iterable[ItemStack] = {
    val components = (0 until getContainerSize).collect {
      case slot if !getItem(slot).isEmpty && isComponentSlot(slot, getItem(slot)) => getItem(slot)
    }

    components.asJava
  }


  override def onMachineConnect(node: api.network.Node): Unit = this.onConnect(node)

  override def onMachineDisconnect(node: api.network.Node): Unit = this.onDisconnect(node)

  def hasRedstoneCard: Boolean = items.exists {
    case item if !item.isEmpty => machine.isRunning && DriverRedstoneCard.worksWith(item, getClass)
    case _ => false
  }

  // ----------------------------------------------------------------------- //

  override def updateEntity(): Unit = {
    // If we're not yet in a network we might have just been loaded from disk,
    // meaning there may be other block entities that also have not re-joined
    // the network. We skip the update this round to allow other block entities
    // to join the network, too, avoiding issues of missing nodes (e.g. in the
    // GPU which would otherwise loose track of its screen).
    if (isServer && isConnected) {
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

    super.updateEntity()
  }

  protected def updateComputer(): Unit = {
    machine.update()
  }

  /** Used by the optional Create integration while this block entity is off-world. */
  def tickMoving(): Unit = updateEntity()

  /** Save the live machine/component state back into Create's captured NBT. */
  override def saveMovingState(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    saveAdditional(nbt, provider)
    // One-shot marker. A normal chunk save writes fresh NBT and drops it after
    // the restored machine has consumed the grace period.
    nbt.putInt(MovingRestoreGraceTag, 40)
  }

  /** Dispose the temporary machine and its component nodes before reassembly. */
  override def disposeMoving(): Unit = {
    // Removing the machine node closes the VM synchronously, then its normal
    // onDisconnect callback removes internal components. Doing that cleanup
    // first would feed fake component_removed events to the still-live VM.
    super.disposeMoving()
  }

  protected def onRunningChanged(): Unit = {
    setChanged()
    ServerPacketSender.sendComputerState(this)
  }

  override def dispose(): Unit = {
    super.dispose()
    if (machine != null && !this.isInstanceOf[RobotProxy]) {
      machine.stop()
    }
  }

  // ----------------------------------------------------------------------- //

  private final val ComputerTag = Settings.namespace + "computer"
  private final val HasErroredTag = Settings.namespace + "hasErrored"
  private final val IsRunningTag = Settings.namespace + "isRunning"
  private final val UsersTag = Settings.namespace + "users"
  private final val MovingRestoreGraceTag = Settings.namespace + "movingRestoreGrace"
  private var pendingMovingRestoreGrace = 0

  override def loadAdditional(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    pendingMovingRestoreGrace = nbt.getInt(MovingRestoreGraceTag)
    super.loadAdditional(nbt, provider)
  }

  override def loadComponentsForServer(holder: DataComponentHolder): Unit = {
    super.loadComponentsForServer(holder)
    // God, this is so ugly... will need to rework the robot architecture.
    // This is required for loading auxiliary data (kernel state), because the
    // coordinates in the actual robot won't be set properly, otherwise.
    this match {
      case proxy: RobotProxy => proxy.robot.setLevel(getLevel)
      case _ =>
    }

    // BlockEntity.loadStatic invokes loadWithComponents before assigning the
    // level. Machine loading needs the dimension and registry, so defer only
    // that part until clearRemoved/initialize runs with a live level.
    if (getLevel == null) pendingMachineData = holder.getComponents
    else loadMachineData(holder)
  }

  private def loadMachineData(holder: DataComponentHolder): Unit = {
    machine.loadData(holder)
    if (pendingMovingRestoreGrace > 0) {
      machine match {
        case implementation: li.cil.oc.server.machine.Machine =>
          implementation.beginComponentRestoreGrace(pendingMovingRestoreGrace)
        case _ =>
      }
      pendingMovingRestoreGrace = 0
    }

    // Kickstart initialization to avoid values getting overwritten by
    // loadForClient if that packet is handled after a manual
    // initialization / state change packet.
    setRunning(machine.isRunning)
    _isOutputEnabled = hasRedstoneCard
  }

  override protected def initialize(): Unit = {
    super.initialize()
    if (isServer && getLevel != null && pendingMachineData != null) {
      val data = pendingMachineData
      pendingMachineData = null
      loadMachineData(Persistable.holder(data))
    }
  }

  override def saveComponentsForServer(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForServer(holder)
    if(machine != null) {
      machine.saveData(holder)
    }
  }

  override def loadComponentsForClient(holder: DataComponentHolder): Unit = {
    super.loadComponentsForClient(holder)
    hasErrored = holder.has(OCComponents.IS_ERRORED)
    setRunning(holder.getComponent(OCComponents.IS_RUNNING) getOrElse false)
    _users.clear()
    for(users <- holder.getComponent(OCComponents.USERS))
      _users ++= users
    for(sound <- runSound if _isRunning)
      Sound.startLoop(this, sound, 0.5f, (1000 + getLevel.random.nextInt(2000)).toLong)
  }

  override def saveComponentsForClient(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForClient(holder)
    holder.setComponent(OCComponents.IS_ERRORED, machine != null && machine.lastError != null)
    holder.setComponent(OCComponents.IS_RUNNING, isRunning)
    if(machine != null) holder.setComponent(OCComponents.USERS, machine.users.toSet)
  }

  // ----------------------------------------------------------------------- //

  override def setChanged(): Unit = {
    super.setChanged()
    if (isServer) {
      machine.onHostChanged()
      setOutputEnabled(hasRedstoneCard)
    }
  }

  override def stillValid(player: Player): Boolean =
    super.stillValid(player) && (player match {
      case fakePlayer: agent.Player => canInteract(fakePlayer.agent.ownerName())
      case _ => canInteract(player.getName.getString)
    })

  override protected def onRotationChanged(): Unit = {
    super.onRotationChanged()
    checkRedstoneInputChanged()
  }

  override protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    val toLocalArgs = RedstoneChangedEventArgs(toLocal(args.side), args.oldValue, args.newValue, args.color)
    machine.node.sendToNeighbors("redstone.changed", toLocalArgs)
  }

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: net.minecraft.world.entity.player.Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = Array(machine.node)
}
