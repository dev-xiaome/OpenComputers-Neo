package li.cil.oc.common.tileentity

import java.util.UUID

import li.cil.oc._
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.item
import li.cil.oc.api.driver.item.Container
import li.cil.oc.api.event.RobotAnalyzeEvent
import li.cil.oc.api.internal
import li.cil.oc.api.network._
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.inventory.InventoryProxy
import li.cil.oc.common.inventory.InventorySelection
import li.cil.oc.common.inventory.TankSelection
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

import scala.collection.mutable

// Implementation note: this tile entity is never directly added to the world.
// It is always wrapped by a `RobotProxy` tile entity, which forwards any
// necessary calls to this class. This is done to make moves efficient: when a
// robot moves we only create a new proxy tile entity, hook the instance of this
// class that was held by the old proxy to it and can then safely forget the
// old proxy, which will be cleaned up by Minecraft like any other tile entity.
//
// 1.21.1 移植说明：本类仍然继承 `BlockEntityBase`（因为 `traits.Computer` 等 trait 的
// 自类型要求 `BlockEntity`），但它**从不加入世界**：位置与世界由构造函数显式注入，
// 机器 / 节点 / 同步全部由外层 [[RobotProxy]] 负责。
class Robot(robotLevel: Level, initialPos: BlockPos, robotState: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(if (robotState == null) null else robotState.getBlock), initialPos, robotState)
    with traits.Computer
    with traits.PowerInformation
    with internal.Robot
    with InventorySelection
    with TankSelection {

  /**
   * 机器人实例由 [[RobotProxy]] 构造并持有（原 1.7.10 是 `RobotProxy(val robot: Robot)`）。
   * 反向引用在这里由代理在 `initialize()` 里回填，用于取机器实例与节点。
   */
  var proxy: RobotProxy = _

  /** 机器人真实位置（代理每次初始化 / 移动后同步过来）。 */
  private var robotPos: BlockPos = if (initialPos == null) BlockPos.ZERO else initialPos

  // ----------------------------------------------------------------------- //
  // 内部机器人组件占位（原 `server.component.Robot`）

  /**
   * 机器人内部组件（原 `li.cil.oc.server.component.Robot`）。
   *
   * TODO(server.component): `server.component` 尚未移植，这里用一个最小占位实现，
   * 只保留节点与存读档表面。组件层移植后请替换为 `new component.Robot(this)`。
   */
  val bot: Robot.BotStub = new Robot.BotStub(this)

  val info = new RobotData()

  if (isServer) {
    if (machine != null) machine.setCostPerTick(Settings.get.robotCost)
  }

  // ----------------------------------------------------------------------- //

  override def world: Level = robotLevel

  override def getLevel: Level = robotLevel

  override def getBlockPos: BlockPos = robotPos

  /** 由代理在自身初始化 / 机器人移动时调用，保持坐标一致。 */
  def setPosition(pos: BlockPos): Unit = if (pos != null) {
    robotPos = pos
  }

  /** 本机器人所属的机器宿主（即外层代理）。 */
  def machineHost: RobotProxy = proxy

  /** 机器实例由代理持有，这里只做转发。 */
  override def machine: api.machine.Machine = if (proxy != null) proxy.machine else null

  /** 节点即机器节点，由代理提供。 */
  override def node: Node = if (proxy != null) proxy.node else null

  override def tier = info.tier

  def isCreative = tier == Tier.Four

  val equipmentInventory = new InventoryProxy {
    override def inventory = Robot.this

    override def getSlots = 4
  }

  // Wrapper for the part of the inventory that is mutable.
  val mainInventory = new InventoryProxy {
    override def inventory = Robot.this

    override def getSlots = Robot.this.inventorySize

    override def offset = equipmentInventory.getSlots
  }

  val actualInventorySize = 100

  def maxInventorySize = actualInventorySize - equipmentInventory.getSlots - componentCount

  var inventorySize = -1

  var selectedSlot = 0

  override def selectedSlot_=(value: Int): Unit = setSelectedSlot(value)

  override def setSelectedSlot(index: Int): Unit = {
    selectedSlot = index max 0 min mainInventory.getSlots - 1
    // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotSelectedSlotChange(this)。
    // 网络层移植后改为发送 SelectedSlotChange 包，这里退化为方块更新。
    if (world != null) markBlockForUpdate()
  }

  val tank: internal.MultiTank = new internal.MultiTank {
    override def tankCount = Robot.this.tankCount

    override def getFluidTank(index: Int) = Robot.this.getFluidTank(index)
  }

  var selectedTank = 0

  override def selectedTank_=(value: Int): Unit = setSelectedTank(value)

  override def setSelectedTank(index: Int): Unit = selectedTank = index

  // For client.
  var renderingErrored = false

  override def componentCount = info.components.length

  override def getComponentInSlot(index: Int) =
    if (index >= 0 && index < components.length) components(index).orNull else null

  /**
   * 机器人使用的假玩家。
   *
   * TODO(server.agent): 原实现为 `server.agent.Player`（可写位置 / 朝向 / 物品栏的假玩家），
   * 并调用 `agent.Player.updatePositionAndRotation` / `setInventoryPlayerItems` 同步状态。
   * `server.agent` 尚未移植，这里退化为 NeoForge 的通用假玩家（只读、不可交互）。
   */
  override def player: Player = if (proxy != null) proxy.fakePlayer else null

  override def synchronizeSlot(slot: Int): Unit = if (slot >= 0 && slot < getSlots) this.synchronized {
    val stack = getStackInSlot(slot)
    // TODO(server.PacketSender): 原实现在这里顺手把组件状态写回物品并调用
    // ServerPacketSender.sendRobotInventory(this, slot, stack)。网络层移植后补回。
  }

  def containerSlots: Range = 1 to info.containers.length

  def componentSlots: Range = getSlots - componentCount until getSlots

  def inventorySlots: Range = equipmentInventory.getSlots until (equipmentInventory.getSlots + mainInventory.getSlots)

  def setLightColor(value: Int): Unit = {
    info.lightColor = value
    // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotLightChange(this)。
    markBlockForUpdate()
  }

  override def shouldAnimate = isRunning

  // ----------------------------------------------------------------------- //

  var globalBuffer, globalBufferSize = 0.0

  val maxComponents = 32

  var ownerName = Settings.get.fakePlayerName

  var ownerUUID = Settings.get.fakePlayerProfile.getId

  var animationTicksLeft = 0

  var animationTicksTotal = 0

  var moveFromX, moveFromY, moveFromZ = Int.MaxValue

  var swingingTool = false

  var turnAxis = 0

  var appliedToolEnchantments = false

  // ----------------------------------------------------------------------- //

  override def name = info.name

  override def setName(name: String): Unit = info.name = name

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    if (player != null) {
      player.sendSystemMessage(Component.literal(Localization.Analyzer.RobotOwner(ownerName)))
      player.sendSystemMessage(Component.literal(Localization.Analyzer.RobotName(name)))
    }
    // 原为 MinecraftForge.EVENT_BUS.post(...)，1.21.1 改用 NeoForge 事件总线。
    NeoForge.EVENT_BUS.post(new RobotAnalyzeEvent(this, player))
    super.onAnalyze(player, side, hitX, hitY, hitZ)
  }

  /**
   * 让机器人朝指定方向移动一格。
   *
   * TODO(server.agent): 原实现会在 `common.block.RobotProxy.moving` 里挂上本实例，然后通过
   * `world.setBlock` 在原位置留下残影（`robotAfterimage`）并在新位置创建新的代理方块实体，
   * 从而复用本对象。这套逻辑依赖 `server.agent` / 方块侧 `RobotProxy.moving` 的完整移植，
   * 目前仅更新自身位置并返回是否「移动成功」的保守值（false）。
   */
  def move(direction: Direction): Boolean = {
    // TODO(server.agent): 待 server.agent / server.component 移植后接回真实逻辑。
    false
  }

  // ----------------------------------------------------------------------- //

  def isAnimatingMove = animationTicksLeft > 0 && (moveFromX != Int.MaxValue || moveFromY != Int.MaxValue || moveFromZ != Int.MaxValue)

  def isAnimatingSwing = animationTicksLeft > 0 && swingingTool

  def isAnimatingTurn = animationTicksLeft > 0 && turnAxis != 0

  def animateSwing(duration: Double): Unit = if (tools(0).isDefined) {
    setAnimateSwing((duration * 20).toInt)
    // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotAnimateSwing(this)。
    markBlockForUpdate()
  }

  def animateTurn(clockwise: Boolean, duration: Double): Unit = {
    setAnimateTurn(if (clockwise) 1 else -1, (duration * 20).toInt)
    // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotAnimateTurn(this)。
    markBlockForUpdate()
  }

  def setAnimateMove(fromPosition: BlockPosition, ticks: Int): Unit = {
    animationTicksTotal = ticks + 2
    prepareForAnimation()
    moveFromX = fromPosition.x
    moveFromY = fromPosition.y
    moveFromZ = fromPosition.z
  }

  def setAnimateSwing(ticks: Int): Unit = {
    animationTicksTotal = math.max(ticks, 5)
    prepareForAnimation()
    swingingTool = true
  }

  def setAnimateTurn(axis: Int, ticks: Int): Unit = {
    animationTicksTotal = ticks
    prepareForAnimation()
    turnAxis = axis
  }

  private def prepareForAnimation(): Unit = {
    animationTicksLeft = animationTicksTotal
    moveFromX = Int.MaxValue
    moveFromY = Int.MaxValue
    moveFromZ = Int.MaxValue
    swingingTool = false
    turnAxis = 0
  }

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    if (animationTicksLeft > 0) {
      animationTicksLeft -= 1
      if (animationTicksLeft == 0) {
        moveFromX = Int.MaxValue
        moveFromY = Int.MaxValue
        moveFromZ = Int.MaxValue
        swingingTool = false
        turnAxis = 0
      }
    }
    super.tick()
    if (isServer) {
      if (world != null && world.getGameTime % Settings.get.tickFrequency == 0) {
        val botNode = bot.node
        if (info.tier == 3 && botNode != null) {
          botNode.changeBuffer(Double.PositiveInfinity)
        }
        if (botNode != null) {
          globalBuffer = botNode.globalBuffer
          globalBufferSize = botNode.globalBufferSize
          info.totalEnergy = globalBuffer.toInt
          info.robotEnergy = botNode.localBuffer.toInt
        }
        updatePowerInformation()
      }
      if (!appliedToolEnchantments) {
        appliedToolEnchantments = true
        // TODO(server.agent): 原实现给机器人假玩家套用工具的属性修饰符
        // （`player_.getAttributeMap.applyAttributeModifiers(...)`）。假玩家移植后恢复。
      }
    }
    // 原实现在这里调用 `ItemStack#updateAnimation`（1.7.10 的 OC 补丁方法，
    // 1.21.1 已不存在），工具动画改由渲染层处理。
  }

  // The robot's machine is updated in a tick handler, to avoid delayed tile
  // entity creation when moving, which would screw over all the things...
  override protected def updateComputer(): Unit = {}

  override protected def onRunningChanged(): Unit = {
    super.onRunningChanged()
    // TODO(common.EventHandler): 原为 EventHandler.onRobotStart / onRobotStopped(this)。
    // `common.EventHandler` 未纳入编译范围（1.21.1 的 ticker 由方块侧决定），暂不处理。
  }

  /**
   * 机器人本体初始化。
   *
   * 注意：这里不能用 `protected`（trait 里是 `protected`，但外层 [[RobotProxy]] 需要调用它），
   * 因此放宽为 `public`；原 1.7.10 是由代理的 `validate()` 调用的。
   */
  override def initialize(): Unit = {
    if (isServer && node != null) {
      // Ensure we have a node address, because the proxy needs this to initialize
      // its own node to the same address ours has.
      api.Network.joinNewNetwork(node)
    }
  }

  override def dispose(): Unit = {
    // 机器与节点归外层代理所有，代理会自行处理销毁流程。
    // TODO(client.gui): 原实现会在客户端打开着本机器人的 GUI 时关闭它。
  }

  /** 本实例不参与 1.21.1 的方块实体生命周期（它从不加入世界）。 */
  override def onLoad(): Unit = {}

  override def setRemoved(): Unit = {}

  override def onChunkUnloaded(): Unit = {}

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    updateInventorySize()
    if (machine != null) machine.onHostChanged()

    if (nbt.contains(Settings.namespace + "robot")) {
      bot.load(nbt.getCompound(Settings.namespace + "robot"))
    }
    if (nbt.contains(Settings.namespace + "owner")) {
      ownerName = nbt.getString(Settings.namespace + "owner")
    }
    if (nbt.contains(Settings.namespace + "ownerUuid")) {
      ownerUUID = UUID.fromString(nbt.getString(Settings.namespace + "ownerUuid"))
    }
    if (inventorySize > 0) {
      selectedSlot = nbt.getInt(Settings.namespace + "selectedSlot") max 0 min mainInventory.getSlots - 1
    }
    selectedTank = nbt.getInt(Settings.namespace + "selectedTank")
    animationTicksTotal = nbt.getInt(Settings.namespace + "animationTicksTotal")
    animationTicksLeft = nbt.getInt(Settings.namespace + "animationTicksLeft")
    if (animationTicksLeft > 0) {
      moveFromX = nbt.getInt(Settings.namespace + "moveFromX")
      moveFromY = nbt.getInt(Settings.namespace + "moveFromY")
      moveFromZ = nbt.getInt(Settings.namespace + "moveFromZ")
      swingingTool = nbt.getBoolean(Settings.namespace + "swingingTool")
      turnAxis = nbt.getByte(Settings.namespace + "turnAxis")
    }

    // Normally set in superclass, but that's not called directly, only in the
    // robot's proxy instance.
    _isOutputEnabled = hasRedstoneCard
    _isAbstractBusAvailable = hasAbstractBusCard
    // TODO(common.EventHandler): 原为 `if (isRunning) EventHandler.onRobotStart(this)`。
  }

  // Side check for Waila (and other mods that may call this client side).
  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = if (isServer) this.synchronized {
    info.save(nbt)

    // Note: computer is saved when proxy is saved (in proxy's super writeToNBT)
    // which is a bit ugly, and may be refactored some day, but it works.
    nbt.put(Settings.namespace + "robot", bot.save)
    nbt.putString(Settings.namespace + "owner", ownerName)
    nbt.putString(Settings.namespace + "ownerUuid", ownerUUID.toString)
    nbt.putInt(Settings.namespace + "selectedSlot", selectedSlot)
    nbt.putInt(Settings.namespace + "selectedTank", selectedTank)
    if (isAnimatingMove || isAnimatingSwing || isAnimatingTurn) {
      nbt.putInt(Settings.namespace + "animationTicksTotal", animationTicksTotal)
      nbt.putInt(Settings.namespace + "animationTicksLeft", animationTicksLeft)
      nbt.putInt(Settings.namespace + "moveFromX", moveFromX)
      nbt.putInt(Settings.namespace + "moveFromY", moveFromY)
      nbt.putInt(Settings.namespace + "moveFromZ", moveFromZ)
      nbt.putBoolean(Settings.namespace + "swingingTool", swingingTool)
      nbt.putByte(Settings.namespace + "turnAxis", turnAxis.toByte)
    }
  }

  /** 仅客户端使用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。 */
  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    load(nbt)
    info.load(nbt)

    updateInventorySize()

    selectedSlot = nbt.getInt("selectedSlot")
    animationTicksTotal = nbt.getInt("animationTicksTotal")
    animationTicksLeft = nbt.getInt("animationTicksLeft")
    moveFromX = nbt.getInt("moveFromX")
    moveFromY = nbt.getInt("moveFromY")
    moveFromZ = nbt.getInt("moveFromZ")
    if (animationTicksLeft > 0) {
      swingingTool = nbt.getBoolean("swingingTool")
      turnAxis = nbt.getByte("turnAxis")
    }
    connectComponents()
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = this.synchronized {
    super.writeToNBTForClient(nbt)
    save(nbt)
    info.save(nbt)

    nbt.putInt("selectedSlot", selectedSlot)
    if (isAnimatingMove || isAnimatingSwing || isAnimatingTurn) {
      nbt.putInt("animationTicksTotal", animationTicksTotal)
      nbt.putInt("animationTicksLeft", animationTicksLeft)
      nbt.putInt("moveFromX", moveFromX)
      nbt.putInt("moveFromY", moveFromY)
      nbt.putInt("moveFromZ", moveFromZ)
      nbt.putBoolean("swingingTool", swingingTool)
      nbt.putByte("turnAxis", turnAxis.toByte)
    }
  }

  // ----------------------------------------------------------------------- //

  override def onMachineConnect(node: Node): Unit = {
    super.onMachineConnect(node)
    if (node != null && node == this.node) {
      if (bot.node != null) node.connect(bot.node)
      node match {
        case connector: Connector => connector.setLocalBufferSize(0)
        case _ =>
      }
    }
  }

  override def onMachineDisconnect(node: Node): Unit = {
    super.onMachineDisconnect(node)
    if (node != null && node == this.node) {
      node.remove()
      if (bot.node != null) bot.node.remove()
      for (slot <- componentSlots) {
        Option(getComponentInSlot(slot)).foreach(component => Option(component.node).foreach(_.remove()))
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    if (isServer) {
      if (isToolSlot(slot)) {
        // TODO(server.agent): 原实现把工具的属性修饰符套用到机器人假玩家上。
      }
      if (isUpgradeSlot(slot)) {
        // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotInventory(this, slot, stack)。
      }
      if (isFloppySlot(slot)) {
        li.cil.oc.common.Sound.playDiskInsert(this)
      }
      if (isComponentSlot(slot, stack)) {
        super.onItemAdded(slot, stack)
        if (world != null) notifyNeighbors()
      }
      if (isInventorySlot(slot)) {
        if (machine != null) machine.signal("inventory_changed", Int.box(slot - equipmentInventory.getSlots + 1))
      }
    }
    else super.onItemAdded(slot, stack)
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (isServer) {
      if (isToolSlot(slot)) {
        // TODO(server.agent): 原实现移除机器人假玩家上的属性修饰符。
      }
      if (isUpgradeSlot(slot)) {
        // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotInventory(this, slot, null)。
      }
      if (isFloppySlot(slot)) {
        li.cil.oc.common.Sound.playDiskEject(this)
      }
      if (isInventorySlot(slot)) {
        if (machine != null) machine.signal("inventory_changed", Int.box(slot - equipmentInventory.getSlots + 1))
      }
      if (isComponentSlot(slot, stack)) {
        if (world != null) notifyNeighbors()
      }
    }
  }

  override def markDirty(): Unit = {
    super.markDirty()
    // Avoid getting into a bad state on the client when updating before we
    // got the descriptor packet from the server. If we manage to open the
    // GUI before the descriptor packet arrived, close it again because it is
    // invalid anyway.
    if (inventorySize >= 0) {
      updateInventorySize()
    }
    // TODO(client.gui): 客户端原实现在这里关闭无效的机器人 GUI。
    renderingErrored = false
  }

  override protected def connectItemNode(node: Node): Unit = {
    super.connectItemNode(node)
    if (node != null) node.host match {
      case buffer: api.internal.TextBuffer =>
        for (slot <- componentSlots) {
          getComponentInSlot(slot) match {
            case keyboard: api.internal.Keyboard => buffer.node.connect(keyboard.node)
            // TODO(server.component): 原实现还会把 GPU（`server.component.GraphicsCard`）
            // 接到显存缓冲上。`server.component` 尚未移植，暂时只处理键盘。
            case _ =>
          }
        }
      case keyboard: api.internal.Keyboard =>
        for (slot <- componentSlots) {
          getComponentInSlot(slot) match {
            case buffer: api.internal.TextBuffer => keyboard.node.connect(buffer.node)
            case _ =>
          }
        }
      case _ =>
    }
  }

  override def isComponentSlot(slot: Int, stack: ItemStack) = (containerSlots ++ componentSlots) contains slot

  def containerSlotType(slot: Int) = if (containerSlots contains slot) {
    val stack = info.containers(slot - 1)
    Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver: Container) => driver.providedSlot(stack)
      case _ => Slot.None
    }
  }
  else Slot.None

  def containerSlotTier(slot: Int) = if (containerSlots contains slot) {
    val stack = info.containers(slot - 1)
    Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver: Container) => driver.providedTier(stack)
      case _ => Tier.None
    }
  }
  else Tier.None

  /** 工具槽的内容（原 `items(0)`）。 */
  def tools: Array[Option[ItemStack]] = Array(Option(getStackInSlot(0)))

  def isToolSlot(slot: Int) = slot == 0

  def isContainerSlot(slot: Int) = containerSlots contains slot

  def isInventorySlot(slot: Int) = inventorySlots contains slot

  def isFloppySlot(slot: Int) = {
    val stack = getStackInSlot(slot)
    stack != null && !stack.isEmpty && isComponentSlot(slot, stack) && {
      Option(Driver.driverFor(stack, getClass)) match {
        case Some(driver) => driver.slot(stack) == Slot.Floppy
        case _ => false
      }
    }
  }

  def isUpgradeSlot(slot: Int) = containerSlotType(slot) == Slot.Upgrade

  // ----------------------------------------------------------------------- //

  override def componentSlot(address: String) =
    components.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  /**
   * 是否安装了红石卡。
   *
   * TODO(integration.opencomputers): 原实现为
   * `(...).exists(slot => Option(getStackInSlot(slot)).fold(false)(DriverRedstoneCard.worksWith(_, getClass)))`。
   * 红石卡驱动位于尚未移植的 `integration.opencomputers` 包，这里退化为基类实现（恒 false）；
   * 集成层移植后请恢复按槽位判定。
   */
  override def hasRedstoneCard = super.hasRedstoneCard

  private def computeInventorySize() = math.min(maxInventorySize, (containerSlots ++ componentSlots).foldLeft(0)((acc, slot) => acc + (Option(getStackInSlot(slot)) match {
    case Some(stack) if stack != null && !stack.isEmpty => Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver: item.Inventory) => driver.inventoryCapacity(stack)
      case _ => 0
    }
    case _ => 0
  })))

  private var updatingInventorySize = false

  def updateInventorySize(): Unit = this.synchronized(if (!updatingInventorySize) try {
    updatingInventorySize = true
    val newInventorySize = computeInventorySize()
    if (newInventorySize != inventorySize) {
      inventorySize = newInventorySize
      val realSize = equipmentInventory.getSlots + mainInventory.getSlots
      val oldSelected = selectedSlot
      val removed = mutable.ArrayBuffer.empty[ItemStack]
      for (slot <- realSize until getSlots - componentCount) {
        val stack = getStackInSlot(slot)
        setInventorySlotContents(slot, null)
        if (stack != null && !stack.isEmpty) removed += stack
      }
      if (components.nonEmpty) {
        val copyComponentCount = math.min(getSlots, componentCount)
        Array.copy(components, getSlots - copyComponentCount, components, realSize, copyComponentCount)
        for (slot <- math.max(0, getSlots - componentCount) until getSlots if slot < realSize || slot >= realSize + componentCount) {
          components(slot) = None
        }
      }
      setSizeInventory(realSize + componentCount)
      if (world != null && isServer) {
        for (stack <- removed) {
          spawnStackInWorld(stack, Option(facing))
        }
        setSelectedSlot(oldSelected)
      } // else: save is screwed and we potentially lose items. Life is hard.
    }
  }
  finally {
    updatingInventorySize = false
  })

  // ----------------------------------------------------------------------- //
  // 物品栏（`IItemHandler`；原 1.7.10 为 `IInventory` / `ISidedInventory`）

  var getSizeInventory = actualInventorySize

  /** 设置槽位总数（原 `getSizeInventory` 因为是 `var`，可以直接赋值）。 */
  def setSizeInventory(value: Int): Unit = getSizeInventory = value

  override def getSlots: Int = getSizeInventory

  override def getInventoryStackLimit = 64

  override def getStackInSlot(slot: Int): ItemStack = {
    if (slot < 0 || slot >= getSizeInventory) null // Required to always show 16 inventory slots in GUI.
    else if (slot >= getSizeInventory - componentCount) {
      if (info.components.length > 0) info.components(slot - (getSizeInventory - componentCount)) else null
    }
    else super.getStackInSlot(slot)
  }

  override def setInventorySlotContents(slot: Int, stack: ItemStack): Unit = {
    if (slot < getSizeInventory - componentCount && (isItemValidForSlot(slot, stack) || stack == null)) {
      if (stack != null && stack.getCount > 1 && isComponentSlot(slot, stack)) {
        super.setInventorySlotContents(slot, stack.split(1))
        if (stack.getCount > 0 && isServer) spawnStackInWorld(stack, Option(facing))
      }
      else super.setInventorySlotContents(slot, stack)
    }
    else if (stack != null && stack.getCount > 0 && !world.isClientSide) spawnStackInWorld(stack, Option(Direction.UP))
  }

  /**
   * 是否允许该玩家使用本机器人的物品栏。
   *
   * TODO(server.agent): 原实现额外判断 `!isCreative || player.capabilities.isCreativeMode`；
   * 1.21.1 的能力（abilities）只在 `ServerPlayer` 上可用，这里退化为「非创造等级机器人」
   * 或玩家为创造模式判断留给 GUI 层处理。
   */
  override def isUseableByPlayer(player: Player): Boolean =
    super.isUseableByPlayer(player) && player != null && player.isCreative

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = isItemValidForSlot(slot, stack)

  def isItemValidForSlot(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (0, _) => true // Allow anything in the tool slot.
    case (i, Some(driver)) if isContainerSlot(i) =>
      // Yay special cases! Dynamic screens kind of work, but are pretty derpy
      // because the item gets send around on changes, including the screen
      // state, which leads to weird effects. Also, it's really illogical that
      // a screen (and keyboard) could be attached to the robot on the fly.
      // Since these are very special (as they have special behavior in the
      // GUI) I feel it's OK to handle it like this, instead of some extra API
      // logic making the differentiation of assembler and containers generic.
      //
      // TODO(integration.opencomputers): 原实现还排除 DriverScreen / DriverKeyboard。
      // 这两个驱动位于尚未移植的 `integration.opencomputers` 包，暂时按类名比较保留同样语义。
      !RobotData.isScreenDriver(driver) &&
        !Robot.isKeyboardDriver(driver) &&
        driver.slot(stack) == containerSlotType(i) &&
        driver.tier(stack) <= containerSlotTier(i)
    case (i, _) if isInventorySlot(i) => true // Normal inventory.
    case _ => false // Invalid slot.
  }

  // ----------------------------------------------------------------------- //

  override def dropSlot(slot: Int, count: Int, direction: Option[Direction]) =
    InventoryUtils.dropSlot(position, mainInventory, slot, count, direction)

  override def dropAllSlots(): Unit = {
    InventoryUtils.dropSlot(position, this, 0, Int.MaxValue)
    for (slot <- containerSlots) {
      InventoryUtils.dropSlot(position, this, slot, Int.MaxValue)
    }
    InventoryUtils.dropAllSlots(position, mainInventory)
  }

  // ----------------------------------------------------------------------- //
  // 按面暴露的槽位（原 `ISidedInventory`，1.21.1 用 `IItemHandler` + 面查询替代）

  def canExtractItem(slot: Int, stack: ItemStack, side: Int) =
    getAccessibleSlotsFromSide(side).contains(slot)

  def canInsertItem(slot: Int, stack: ItemStack, side: Int) =
    getAccessibleSlotsFromSide(side).contains(slot) &&
      isItemValidForSlot(slot, stack)

  def getAccessibleSlotsFromSide(side: Int) =
    toLocal(Direction.from3DDataValue(side)) match {
      case Direction.WEST => Array(0) // Tool
      case Direction.EAST => containerSlots.toArray
      case _ => inventorySlots.toArray
    }

  /** 由 [[RobotProxy]] 的 `itemHandler(side)` 调用，等价于 1.7.10 的按面物品栏视图。 */
  def itemHandler(side: Direction): net.neoforged.neoforge.items.IItemHandler =
    new Robot.SidedItemHandler(this, side)

  // ----------------------------------------------------------------------- //
  // 储罐（原 1.7.10 的 `IFluidHandler`，1.21.1 为 NeoForge 版 + `MultiTank`）

  /**
   * 取第 `index` 个储罐。
   *
   * TODO(server.component): 1.7.10 里升级槽里的储罐组件是 `server.component.Tank`
   * （实现 `IFluidHandler` 的单罐）。`server.component` 未纳入本次编译范围，
   * 因此这里只按 `IFluidHandler` 判定，不依赖具体实现类。
   */
  def tryGetTank(index: Int): Option[IFluidHandler] = {
    val tanks = components.collect {
      case Some(t: IFluidHandler) => t
    }
    if (index < 0 || index >= tanks.length) None
    else Option(tanks(index))
  }

  def tankCount = components.count {
    case Some(_: IFluidHandler) => true
    case _ => false
  }

  def getFluidTank(index: Int): IFluidHandler = tryGetTank(index).orNull

  // ----------------------------------------------------------------------- //

  override def getTanks: Int = tankCount

  override def getFluidInTank(tank: Int): FluidStack = tryGetTank(tank) match {
    case Some(t) => Option(t.getFluid).getOrElse(FluidStack.EMPTY)
    case _ => FluidStack.EMPTY
  }

  override def getTankCapacity(tank: Int): Int = tryGetTank(tank) match {
    case Some(t) => t.getCapacity
    case _ => 0
  }

  override def isFluidValid(tank: Int, stack: FluidStack): Boolean = tryGetTank(tank) match {
    case Some(t) => t.isFluidValid(stack)
    case _ => false
  }

  override def fill(resource: FluidStack, action: FluidAction): Int = tryGetTank(selectedTank) match {
    case Some(t) => t.fill(resource, action)
    case _ => 0
  }

  override def drain(resource: FluidStack, action: FluidAction): FluidStack = tryGetTank(selectedTank) match {
    case Some(t) if t.getFluid != null && !t.getFluid.isEmpty && t.getFluid.isFluidEqual(resource) =>
      t.drain(resource, action)
    case _ => FluidStack.EMPTY
  }

  override def drain(maxDrain: Int, action: FluidAction): FluidStack = tryGetTank(selectedTank) match {
    case Some(t) => t.drain(maxDrain, action)
    case _ => FluidStack.EMPTY
  }
}

object Robot {

  /** 按类名判断键盘驱动（1.7.10 里是 `driver != DriverKeyboard`）。 */
  private[tileentity] def isKeyboardDriver(driver: AnyRef): Boolean = driver != null && {
    val name = driver.getClass.getName
    name == "li.cil.oc.integration.opencomputers.DriverKeyboard" ||
      name == "li.cil.oc.integration.opencomputers.DriverKeyboard$"
  }

  /**
   * 机器人内部组件占位实现（原 `li.cil.oc.server.component.Robot`）。
   *
   * TODO(server.component): 该包尚未移植。本占位保留 `node` / `load` / `save` 表面，
   * 让 `Robot` 的调用点可以保持原样；`node` 恒为 `null`（所有使用点都做了空值保护）。
   */
  class BotStub(val robot: Robot) {
    def node: Connector = null

    def update(): Unit = {}

    def load(nbt: CompoundTag): Unit = {}

    def save: CompoundTag = new CompoundTag()
  }

  /**
   * 按面暴露的机器人物品栏视图（原 `ISidedInventory` 的 `getAccessibleSlotsFromSide`）。
   *
   * `side` 为 `null` 时按「机器人自身朝向」处理（等价于原实现的 `Direction.UP` 兜底）。
   */
  class SidedItemHandler(robot: Robot, side: Direction) extends net.neoforged.neoforge.items.IItemHandler {
    private def accessibleSlots: Set[Int] =
      robot.getAccessibleSlotsFromSide(
        if (side == null) Direction.UP.get3DDataValue else side.get3DDataValue).toSet

    override def getSlots: Int = robot.getSlots

    override def getStackInSlot(slot: Int): ItemStack =
      if (accessibleSlots.contains(slot)) Option(robot.getStackInSlot(slot)).getOrElse(ItemStack.EMPTY)
      else ItemStack.EMPTY

    override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack =
      if (accessibleSlots.contains(slot) && robot.canInsertItem(slot, stack, sideValue)) robot.insertItem(slot, stack, simulate)
      else stack

    override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack =
      if (accessibleSlots.contains(slot) && robot.canExtractItem(slot, robot.getStackInSlot(slot), sideValue)) robot.extractItem(slot, amount, simulate)
      else ItemStack.EMPTY

    override def getSlotLimit(slot: Int): Int = robot.getSlotLimit(slot)

    override def isItemValid(slot: Int, stack: ItemStack): Boolean =
      accessibleSlots.contains(slot) && robot.isItemValid(slot, stack)

    private def sideValue: Int = if (side == null) Direction.UP.get3DDataValue else side.get3DDataValue
  }
}
