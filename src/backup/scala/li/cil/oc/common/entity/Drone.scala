package li.cil.oc.common.entity

import java.lang.Iterable
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.mojang.authlib.GameProfile
import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.Machine
import li.cil.oc.api.driver.item
import li.cil.oc.api.internal
import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.machine.Context
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network._
import li.cil.oc.common.GuiType
import li.cil.oc.common.inventory.ComponentInventory
import li.cil.oc.common.inventory.Inventory
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.{EntityDataAccessor, EntityDataSerializers, SynchedEntityData}
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.{Entity, EntityType, MoverType}
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

import scala.jdk.CollectionConverters._

/**
 * 无人机实体（对应 1.7.10 的 `common.entity.Drone`）。
 *
 * ==1.21.1 迁移要点==
 *  - 构造器改为 `(EntityType[Drone], Level)`；`world` 参数被 `Level` 取代（`Entity#level()`）。
 *  - `setSize(w, h)` 已被移除：尺寸改由 [[EntityType.Builder#sized]] 决定，这里用包围盒兜底。
 *  - `isImmuneToFire` 字段 → 覆写 `fireImmune()`。
 *  - `dataWatcher`（数字 id）→ `SynchedEntityData.defineId` + `defineSynchedData`。
 *  - `posX/posY/posZ` → `getX/getY/getZ`；`motionX/Y/Z` → `getDeltaMovement()/setDeltaMovement()`；
 *    `moveEntity(dx, dy, dz)` → `move(MoverType.SELF, Vec3)`；`onGround` 字段 → `onGround()`。
 *  - `isInsideOfMaterial(Material.water/lava)` → `isInWater()` / `isInLava()`。
 *  - `world.rand` → `getRandom`；`world.getTotalWorldTime` → `level.getGameTime`。
 *  - `isDead` → `isRemoved()`；`setDead()` → `discard()`（`setRemoved` 是 final，见 [[remove]]）。
 *  - `copyDataFrom(entity, unused)` → `restoreFrom(entity)`。
 *  - `travelToDimension(int)` → 原版 `changeDimension(DimensionTransition)`（由原版构造，无法覆写插入换算）。
 *  - `handleWaterMovement` 覆写点已不存在，浮力交给原版内置的流体逻辑。
 *  - `getCommandSenderName` → 覆写 `getName`。
 *  - `ItemEntity#delayBeforeCanPickup` → `setPickUpDelay(int)`；`world.spawnEntityInWorld` → `addFreshEntity`。
 *  - `player.openGui(...)` → `player.openMenu(...)`（容器 / GUI 层尚未移植，暂不打开）。
 *  - `InventoryUtils.dropAllSlots` → [[li.cil.oc.util.InventoryUtils.dropAllSlotsHandler]]（`IItemHandler` 版）。
 *
 * ==降级清单==
 *  - `server.agent.Player`（无人机 / 机器人共用的「假玩家」层，属未移植的 `server/agent`）：
 *    [[player]] 退化为「主人在线就返回主人，否则返回原地 FakePlayer」。
 *    真正依赖假玩家精确朝向的逻辑（放方块 / 用物品）在 `server.component.Drone` 里，本轮不涉及。
 *  - `server.component.Drone`（无人机组件层，未移植）：[[control]] 固定为 `null`，
 *    因此 [[initializeAfterPlacement]] / 存档里的 `control` 相关行做了空值保护。
 *  - `integration.util.Wrench`：改为用 `api.Items.get` 判断手持扳手（见 [[isWrench]]）。
 *  - 打开无人机 GUI：见 [[interact]] 的 `TODO(container)`。
 */
class Drone(val entityType: EntityType[Drone], val world: Level)
  extends Entity(entityType, world)
    with MachineHost with internal.Drone with internal.Rotatable with Analyzable with Context {

  // 一些基本常量。
  val gravity = 0.05f
  // 故意取小值：让无人机缓慢下落（飘落）。
  val drag = 0.8f
  val maxAcceleration = 0.1f
  val maxVelocity = 0.4f
  val maxInventorySize = 8

  // 1.21.1：`setSize` 已移除。`EntityType.Builder#sized` 会给出同样的 12x6 像素尺寸，
  // 这里再用包围盒兜底，保证任何构造路径下尺寸都正确。
  setBoundingBox(makeBoundingBox)

  // 纯装饰的渲染数据。
  val targetFlapAngles = Array.fill(4, 2)(0f)
  val flapAngles = Array.fill(4, 2)(0f)
  var nextFlapChange = 0
  var bodyAngle = math.random.toFloat * 90
  var angularVelocity = 0f
  var nextAngularVelocityChange = 0
  var lastEnergyUpdate = 0

  /** 主人 UUID（用于构造假玩家档案），读档时由 [[readAdditionalSaveData]] 恢复。 */
  private var _ownerUUID: UUID = Drone.DefaultOwnerUUID

  /** 主人名字，读档时由 [[readAdditionalSaveData]] 恢复。 */
  private var _ownerName: String = Settings.get.fakePlayerName

  // ----------------------------------------------------------------------- //
  // 逻辑部分：组件、机器等。

  val info = new DroneData()

  val machine = if (!world.isClientSide) {
    val m = Machine.create(this)
    if (m != null && m.node != null) {
      m.node match {
        case connector: Connector => connector.setLocalBufferSize(0)
        case _ =>
      }
    }
    m
  } else null

  // TODO(server.component.Drone): 无人机组件层（Lua 侧 `drone` 组件、状态文本 / 指示灯 API）尚未移植。
  val control: ManagedEnvironment = null

  val components = new ComponentInventory {
    override val items: Array[Option[ItemStack]] = Array.fill[Option[ItemStack]](info.components.length)(None)

    override def host: EnvironmentHost = Drone.this

    override def getSlots: Int = info.components.length

    override def markDirty(): Unit = {}

    override def isItemValid(slot: Int, stack: ItemStack): Boolean = true

    override def node: Node = if (machine == null) null else machine.node

    override def onConnect(node: Node): Unit = {}

    override def onDisconnect(node: Node): Unit = {}

    override def onMessage(message: Message): Unit = {}
  }

  val equipmentInventory: Inventory = new Inventory {
    override val items: Array[Option[ItemStack]] = Array.empty[Option[ItemStack]]

    override def getSlots: Int = 0

    override def getInventoryStackLimit: Int = 0

    override def markDirty(): Unit = {}

    override def isItemValid(slot: Int, stack: ItemStack): Boolean = false
  }

  val mainInventory: Inventory = new Inventory {
    override val items: Array[Option[ItemStack]] = Array.fill[Option[ItemStack]](8)(None)

    override def getSlots: Int = inventorySize

    override def getInventoryStackLimit: Int = 64

    override def markDirty(): Unit = {} // TODO(container): 客户端 GUI 刷新。

    override def isItemValid(slot: Int, stack: ItemStack): Boolean = slot >= 0 && slot < getSlots
  }

  /**
   * 无人机自身没有储罐，储罐来自「储罐升级」等组件。
   *
   * 1.21.1：`IFluidTank` 已被 NeoForge 的 [[IFluidHandler]] 取代，
   * 因此这里把所有组件暴露的流体处理器串成一个只读 / 转发视图。
   */
  val tank: MultiTank with IFluidHandler = new MultiTank with IFluidHandler {
    private def handlers: Seq[IFluidHandler] = components.componentEnvironments.toSeq.flatten.collect {
      case handler: IFluidHandler => handler
    }

    // ---- MultiTank ----

    override def tankCount(): Int = getTanks

    override def getFluidTank(index: Int): IFluidHandler = {
      val outer = this
      new IFluidHandler {
        override def getTanks: Int = 1

        override def getFluidInTank(tank: Int): FluidStack = outer.getFluidInTank(index)

        override def getTankCapacity(tank: Int): Int = outer.getTankCapacity(index)

        override def isFluidValid(tank: Int, stack: FluidStack): Boolean = outer.isFluidValid(index, stack)

        override def fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int =
          outer.fill(resource, action)

        override def drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack =
          outer.drain(resource, action)

        override def drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack =
          outer.drain(maxDrain, action)
      }
    }

    // ---- IFluidHandler ----

    override def getTanks: Int = handlers.map(_.getTanks).sum

    override def getFluidInTank(tank: Int): FluidStack = {
      var remaining = tank
      for (handler <- handlers) {
        val count = handler.getTanks
        if (remaining < count) return handler.getFluidInTank(remaining)
        remaining -= count
      }
      FluidStack.EMPTY
    }

    override def getTankCapacity(tank: Int): Int = {
      var remaining = tank
      for (handler <- handlers) {
        val count = handler.getTanks
        if (remaining < count) return handler.getTankCapacity(remaining)
        remaining -= count
      }
      0
    }

    override def isFluidValid(tank: Int, stack: FluidStack): Boolean = {
      var remaining = tank
      for (handler <- handlers) {
        val count = handler.getTanks
        if (remaining < count) return handler.isFluidValid(remaining, stack)
        remaining -= count
      }
      false
    }

    override def fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int = {
      var filled = 0
      for (handler <- handlers if filled < resource.getAmount) {
        filled += handler.fill(resource.copyWithAmount(resource.getAmount - filled), action)
      }
      filled
    }

    override def drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = {
      var drained = FluidStack.EMPTY
      for (handler <- handlers) {
        drained = Drone.mergeFluid(drained, handler.drain(resource, action))
      }
      drained
    }

    override def drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = {
      var drained = FluidStack.EMPTY
      var remaining = maxDrain
      for (handler <- handlers if remaining > 0) {
        val result = handler.drain(remaining, action)
        if (!result.isEmpty) {
          remaining -= result.getAmount
          drained = Drone.mergeFluid(drained, result)
        }
      }
      drained
    }
  }

  private var _selectedTank = 0

  override def setSelectedTank(index: Int): Unit = _selectedTank = index

  override def selectedTank(): Int = _selectedTank

  override def tier: Int = info.tier

  /**
   * 无人机 / 机器人共用的「代玩家」。
   *
   * TODO(server.agent): 1.7.10 这里返回 `server.agent.Player`（带精确朝向、
   * 自带物品栏映射的假玩家）。该包尚未移植，因此退化为：
   *  - 主人在线 → 直接返回主人（打开容器等场景语义一致）；
   *  - 否则 → 返回一个原地 FakePlayer（不参与方块放置的精细逻辑）。
   */
  override def player(): Player = {
    world match {
      case serverLevel: ServerLevel =>
        val owner = serverLevel.getServer.getPlayerList.getPlayer(_ownerUUID)
        if (owner != null) owner
        else FakePlayerFactory.get(serverLevel, new GameProfile(_ownerUUID, Settings.get.fakePlayerName))
      case _ => null
    }
  }

  override def name: String = info.name

  override def setName(name: String): Unit = info.name = name

  override def ownerName(): String = _ownerName

  override def ownerUUID(): UUID = _ownerUUID

  // ----------------------------------------------------------------------- //
  // 上下文相关调用一律转发给机器；部分组件（例如区块加载升级）依赖这一点。

  override def node: Node = if (machine == null) null else machine.node

  override def canInteract(player: String): Boolean = machine != null && machine.canInteract(player)

  def isRunning: Boolean = machine != null && machine.isRunning

  override def isPaused: Boolean = machine != null && machine.isPaused

  override def start(): Boolean = {
    if (world.isClientSide || machine == null || machine.isRunning) {
      return false
    }
    preparePowerUp()
    machine.start()
  }

  override def pause(seconds: Double): Boolean = machine != null && machine.pause(seconds)

  override def stop(): Boolean = machine != null && machine.stop()

  override def consumeCallBudget(callCost: Double): Unit = if (machine != null) machine.consumeCallBudget(callCost)

  override def signal(name: String, args: AnyRef*): Boolean = machine != null && machine.signal(name, args.toSeq: _*)

  // ----------------------------------------------------------------------- //

  override def getTarget: Vec3 = new Vec3(targetX, targetY, targetZ)

  override def setTarget(value: Vec3): Unit = {
    targetX = value.x.toFloat
    targetY = value.y.toFloat
    targetZ = value.z.toFloat
  }

  override def getVelocity: Vec3 = getDeltaMovement

  // ----------------------------------------------------------------------- //

  override def canBeCollidedWith: Boolean = true

  override def isPushable: Boolean = true

  override def fireImmune(): Boolean = true

  // ----------------------------------------------------------------------- //

  override def xPosition: Double = getX

  override def yPosition: Double = getY

  override def zPosition: Double = getZ

  override def markChanged(): Unit = {}

  // ----------------------------------------------------------------------- //

  override def facing: Direction = Direction.SOUTH

  override def toLocal(value: Direction): Direction = value

  override def toGlobal(value: Direction): Direction = value

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] =
    if (machine == null) Array.empty[Node] else Array(machine.node)

  // ----------------------------------------------------------------------- //

  override def internalComponents(): Iterable[ItemStack] = info.components.toSeq.asJava

  override def componentSlot(address: String): Int =
    components.componentEnvironments.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  override def onMachineConnect(node: Node): Unit = {}

  override def onMachineDisconnect(node: Node): Unit = {}

  def computeInventorySize(): Int = math.min(maxInventorySize, info.components.foldLeft(0)((acc, component) => acc + (Option(component) match {
    case Some(stack) if stack != null && !stack.isEmpty => Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver: item.Inventory) => math.max(1, driver.inventoryCapacity(stack) / 4)
      case _ => 0
    }
    case _ => 0
  })))

  // ----------------------------------------------------------------------- //
  // 同步数据（原 `dataWatcher.addObject(2..12, ...)`）

  override protected def defineSynchedData(builder: SynchedEntityData.Builder): Unit = {
    // 注意：1.21.1 的 `EntityDataAccessor` 对类型参数**不变**，
    // 而 Scala 里 Java 的 `byte` 形参会解析成 Scala 的 `Byte`（与 `java.lang.Byte` 不匹配），
    // 因此这里统一用非原始类型（`java.lang.Integer` / `java.lang.Float` / `String`）承载，
    // 与旧版 `dataWatcher` 的数字编号一一对应。
    // 是否正在运行（0/1）。
    builder.define(Drone.DataRunning, Integer.valueOf(0))
    // 目标位置。
    builder.define(Drone.DataTargetX, java.lang.Float.valueOf(0f))
    builder.define(Drone.DataTargetY, java.lang.Float.valueOf(0f))
    builder.define(Drone.DataTargetZ, java.lang.Float.valueOf(0f))
    // 最大加速度。
    builder.define(Drone.DataTargetAcceleration, java.lang.Float.valueOf(0f))
    // 选中的物品栏槽位。
    builder.define(Drone.DataSelectedSlot, Integer.valueOf(0))
    // 当前 / 最大能量。
    builder.define(Drone.DataGlobalBuffer, Integer.valueOf(0))
    builder.define(Drone.DataGlobalBufferSize, Integer.valueOf(100))
    // 状态文本。
    builder.define(Drone.DataStatusText, "")
    // 客户端需要的物品栏大小。
    builder.define(Drone.DataInventorySize, Integer.valueOf(0))
    // 指示灯颜色。
    builder.define(Drone.DataLightColor, Integer.valueOf(0x66DD55))
  }

  def initializeAfterPlacement(stack: ItemStack, player: Player, position: Vec3): Unit = {
    info.load(stack)
    // TODO(server.component.Drone): `control` 尚未移植，这里做空值保护。
    if (control != null && control.node.isInstanceOf[Connector]) {
      val connector = control.node.asInstanceOf[Connector]
      connector.changeBuffer(info.storedEnergy - connector.localBuffer)
    }
    wireThingsTogether()
    inventorySize = computeInventorySize()
    setPos(position.x, position.y, position.z)
  }

  def preparePowerUp(): Unit = {
    targetX = math.floor(getX).toFloat + 0.5f
    targetY = math.round(getY).toFloat + 0.5f
    targetZ = math.floor(getZ).toFloat + 0.5f
    targetAcceleration = maxAcceleration

    wireThingsTogether()
  }

  private def wireThingsTogether(): Unit = {
    if (machine == null) return
    api.Network.joinNewNetwork(machine.node)
    if (control != null && control.node != null) {
      machine.node.connect(control.node)
    }
    machine.setCostPerTick(Settings.get.droneCost)
    components.connectComponents()
  }

  def targetX: Float = getEntityData.get(Drone.DataTargetX).floatValue

  def targetY: Float = getEntityData.get(Drone.DataTargetY).floatValue

  def targetZ: Float = getEntityData.get(Drone.DataTargetZ).floatValue

  def targetAcceleration: Float = getEntityData.get(Drone.DataTargetAcceleration).floatValue

  def selectedSlot(): Int = getEntityData.get(Drone.DataSelectedSlot).intValue

  def globalBuffer: Int = getEntityData.get(Drone.DataGlobalBuffer).intValue

  def globalBufferSize: Int = getEntityData.get(Drone.DataGlobalBufferSize).intValue

  def statusText: String = getEntityData.get(Drone.DataStatusText)

  def inventorySize: Int = getEntityData.get(Drone.DataInventorySize).intValue

  def lightColor: Int = getEntityData.get(Drone.DataLightColor).intValue

  def setRunning(value: Boolean): Unit = getEntityData.set(Drone.DataRunning, Integer.valueOf(if (value) 1 else 0))

  // 目标值取 1/4 精度，避免浮点误差累积。
  def targetX_=(value: Float): Unit = getEntityData.set(Drone.DataTargetX, java.lang.Float.valueOf(math.round(value * 4) / 4f))

  def targetY_=(value: Float): Unit = getEntityData.set(Drone.DataTargetY, java.lang.Float.valueOf(math.round(value * 4) / 4f))

  def targetZ_=(value: Float): Unit = getEntityData.set(Drone.DataTargetZ, java.lang.Float.valueOf(math.round(value * 4) / 4f))

  def targetAcceleration_=(value: Float): Unit =
    getEntityData.set(Drone.DataTargetAcceleration, java.lang.Float.valueOf(math.max(0, math.min(maxAcceleration, value))))

  def setSelectedSlot(value: Int): Unit = getEntityData.set(Drone.DataSelectedSlot, Integer.valueOf(value))

  def globalBuffer_=(value: Int): Unit = getEntityData.set(Drone.DataGlobalBuffer, Integer.valueOf(value))

  def globalBufferSize_=(value: Int): Unit = getEntityData.set(Drone.DataGlobalBufferSize, Integer.valueOf(value))

  def statusText_=(value: String): Unit =
    getEntityData.set(Drone.DataStatusText, Option(value).fold("")(_.linesIterator.map(_.take(10)).take(2).mkString("\n")))

  def inventorySize_=(value: Int): Unit = getEntityData.set(Drone.DataInventorySize, Integer.valueOf(value))

  def lightColor_=(value: Int): Unit = getEntityData.set(Drone.DataLightColor, Integer.valueOf(value))

  /**
   * 客户端位置插值（1.7.10 的 `setPositionAndRotation2`）。
   *
   * 只有距离服务器位置太远时才硬对齐，否则保持插值 —— 这样能消除抖动，
   * 对无人机来说已经足够。
   */
  override def lerpTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, steps: Int): Unit = {
    if (!isRunning || distanceToSqr(x, y, z) > 1) {
      super.lerpTo(x, y, z, yaw, pitch, steps)
    }
    else {
      targetX = x.toFloat
      targetY = y.toFloat
      targetZ = z.toFloat
    }
  }

  override def tick(): Unit = {
    super.tick()

    if (!world.isClientSide) {
      // 我们不防水！
      if (isInWater || isInLava) {
        stop()
      }
      if (machine != null) {
        machine.update()
        setRunning(machine.isRunning)

        val buffer = math.round(machine.node.asInstanceOf[Connector].globalBuffer).toInt
        if (math.abs(lastEnergyUpdate - buffer) > 1 || world.getGameTime % 200 == 0) {
          lastEnergyUpdate = buffer
          globalBuffer = buffer
          globalBufferSize = machine.node.asInstanceOf[Connector].globalBufferSize.toInt
        }
      }
      components.updateComponents()
    }
    else {
      if (isRunning) {
        // 客户端更新：偶尔改变机翼俯仰与机体旋转，让无人机看起来更「活」。
        val rng = getRandom
        nextFlapChange -= 1
        nextAngularVelocityChange -= 1

        if (nextFlapChange < 0) {
          nextFlapChange = 5 + rng.nextInt(10)
          for (i <- 0 until 2) {
            val flap = rng.nextInt(targetFlapAngles.length)
            targetFlapAngles(flap)(0) = math.toRadians(rng.nextFloat() * 4 - 2).toFloat
            targetFlapAngles(flap)(1) = math.toRadians(rng.nextFloat() * 4 - 2).toFloat
          }
        }

        if (nextAngularVelocityChange < 0) {
          if (angularVelocity != 0) {
            angularVelocity = 0
            nextAngularVelocityChange = 20
          }
          else {
            angularVelocity = if (rng.nextBoolean()) 0.1f else -0.1f
            nextAngularVelocityChange = 100
          }
        }

        // 插值机翼旋转。
        (flapAngles, targetFlapAngles).zipped.foreach((f, t) => {
          f(0) = f(0) * 0.7f + t(0) * 0.3f
          f(1) = f(1) * 0.7f + t(1) * 0.3f
        })

        // 更新机体旋转。
        bodyAngle += angularVelocity
      }
    }

    // 1.21.1 的原版 `baseTick` 已经维护了 `xo/yo/zo` 与 `noPhysics`，
    // 旧版手工同步 `prevPosX/Y/Z` 以及 `func_145771_j`（区块加载检查）的代码不再需要。

    if (isRunning) {
      val toTarget = new Vec3(targetX - getX, targetY - getY, targetZ - getZ)
      val distance = toTarget.length()
      val velocity = getDeltaMovement
      if (distance > 0 && (distance > 0.005f || velocity.dot(velocity) > 0.005f)) {
        val acceleration = math.min(targetAcceleration, distance) / distance
        val updated = velocity.add(toTarget.x * acceleration, toTarget.y * acceleration, toTarget.z * acceleration)
        setDeltaMovement(
          math.max(-maxVelocity, math.min(maxVelocity, updated.x)),
          math.max(-maxVelocity, math.min(maxVelocity, updated.y)),
          math.max(-maxVelocity, math.min(maxVelocity, updated.z)))
      }
      else {
        setDeltaMovement(0, 0, 0)
        setPos(targetX, targetY, targetZ)
      }
    }
    else {
      // 没电了，自由落体！
      setDeltaMovement(getDeltaMovement.x, getDeltaMovement.y - gravity, getDeltaMovement.z)
    }

    move(MoverType.SELF, getDeltaMovement)

    // 保证速度不会无限增大。
    if (isRunning) {
      setDeltaMovement(getDeltaMovement.scale(drag))
    }
    else {
      // 1.21.1：`world.getBlock(pos).slipperiness` → `BlockState#getFriction(level, pos, entity)`。
      val below = new BlockPos(getBlockX, getBlockY - 1, getBlockZ)
      val groundDrag = world.getBlockState(below).getFriction(world, below, this) * drag
      setDeltaMovement(getDeltaMovement.x * groundDrag, getDeltaMovement.y * drag, getDeltaMovement.z * groundDrag)
      if (onGround()) {
        setDeltaMovement(getDeltaMovement.x, getDeltaMovement.y * -0.5, getDeltaMovement.z)
      }
    }
  }

  /**
   * 受到攻击（1.7.10 的 `hitByEntity`）。
   *
   * 1.21.1 的等价入口是 [[Entity#hurt]]：先按旧语义给机器发 `hit` 信号并反弹，
   * 再交给原版处理伤害。
   */
  override def hurt(source: DamageSource, amount: Float): Boolean = {
    val entity = source.getEntity
    if (entity != null && isRunning) {
      val direction = new Vec3(entity.getX - getX, entity.getEyeY - getY, entity.getZ - getZ).normalize()
      if (!world.isClientSide && machine != null) {
        if (Settings.get.inputUsername) {
          machine.signal("hit", Double.box(direction.x), Double.box(direction.z), Double.box(direction.y), entity.getName.getString)
        }
        else {
          machine.signal("hit", Double.box(direction.x), Double.box(direction.z), Double.box(direction.y))
        }
      }
      val delta = getDeltaMovement
      setDeltaMovement(
        (delta.x - direction.x) * 0.5f,
        (delta.y - direction.y) * 0.5f,
        (delta.z - direction.z) * 0.5f)
    }
    super.hurt(source, amount)
  }

  override def interact(player: Player, hand: InteractionHand): InteractionResult = {
    if (isRemoved) return InteractionResult.PASS

    if (player.isShiftKeyDown) {
      if (isWrench(player.getMainHandItem)) {
        if (!world.isClientSide) {
          kill()
        }
      }
      else if (!world.isClientSide && machine != null && !machine.isRunning) {
        start()
      }
    }
    else if (!world.isClientSide) {
      // TODO(container): 原实现 `player.openGui(OpenComputers, GuiType.Drone.id, world, getEntityId, 0, 0)`；
      // 1.21.1 需要 `MenuProvider` / `MenuType`（见 docs/PORTING.md），
      // `common/container` + GUI 层尚未完成，因此这里暂不打开界面。
      val _ = GuiType.Drone.id
    }
    InteractionResult.sidedSuccess(world.isClientSide)
  }

  /**
   * 手持物是否为扳手。
   *
   * TODO(integration.util.Wrench): 原实现调用 `li.cil.oc.integration.util.Wrench.isWrench`，
   * 该包尚未移植；这里按物品名判断（默认只有 OC 自己的扳手算数，语义等价）。
   */
  private def isWrench(stack: ItemStack): Boolean =
    stack != null && !stack.isEmpty && api.Items.get(stack) == api.Items.get(Constants.ItemName.Wrench)

  // 没有脚步声。除了那一天。
  override def playStepSound(pos: BlockPos, state: BlockState): Unit = {
    // TODO(common.EventHandler): 事件层未接入编译集前固定不播放脚步声。\n    if (java.lang.Boolean.getBoolean("opencomputers_neo.stepSounds")) super.playStepSound(pos, state)
  }

  // ----------------------------------------------------------------------- //

  private var isChangingDimension = false

  /**
   * 实体被移除时做清理。
   *
   * 1.21.1：`setDead()` 已移除，`setRemoved` 是 final，因此清理逻辑挂在 [[remove]] 上。
   * 跨维度传送（`RemovalReason.CHANGED_DIMENSION`）不清理，交给「新的自己」。
   */
  override def remove(reason: Entity.RemovalReason): Unit = {
    val shouldCleanUp = !world.isClientSide && !isChangingDimension && reason != Entity.RemovalReason.CHANGED_DIMENSION
    if (shouldCleanUp) {
      if (machine != null) {
        machine.stop()
        machine.node.remove()
      }
      components.disconnectComponents()
      components.saveComponents()
    }
    super.remove(reason)
  }

  /**
   * 跨维度复制实体后的数据修正（1.7.10 的 `copyDataFrom`）。
   *
   * 因为参照系肯定变了（例如去下界坐标会除以 8），这里按旧位置把相对目标点换算回来。
   * 1.7.10 的 `travelToDimension` 覆写（把绝对目标转成相对目标）在 1.21.1 没有对应覆写点，
   * 因此这里只保留「复制后换算」这一半，并把 [[isChangingDimension]] 标志在
   * [[remove]] 里用 `RemovalReason.CHANGED_DIMENSION` 代替。
   */
  override def restoreFrom(entity: Entity): Unit = {
    super.restoreFrom(entity)
    entity match {
      case drone: Drone =>
        targetX = (getX + drone.targetX).toFloat
        targetY = (getY + drone.targetY).toFloat
        targetZ = (getZ + drone.targetZ).toFloat
      case _ =>
        targetX = getX.toFloat
        targetY = getY.toFloat
        targetZ = getZ.toFloat
    }
  }

  override def kill(): Unit = {
    if (isRemoved) return
    if (!world.isClientSide) {
      val stack = api.Items.get(Constants.ItemName.Drone).createItemStack(1)
      info.storedEnergy = if (control != null && control.node.isInstanceOf[Connector]) {
        control.node.asInstanceOf[Connector].localBuffer.toInt
      } else 0
      info.save(stack)

      val dropped = new ItemEntity(world, getX, getY, getZ, stack)
      dropped.setPickUpDelay(15)
      world.addFreshEntity(dropped)

      InventoryUtils.dropAllSlots(BlockPosition(this: Entity), mainInventory)
    }
    discard()
  }

  override def getName: Component = Component.literal(Localization.localizeImmediately("entity.oc.Drone.name"))

  override protected def readAdditionalSaveData(nbt: CompoundTag): Unit = {
    info.load(nbt.getCompound("info"))
    inventorySize = computeInventorySize()
    if (!world.isClientSide) {
      if (machine != null) {
        machine.load(nbt.getCompound("machine"))
      }
      components.load(nbt.getCompound("components"))
      mainInventory.load(nbt.getCompound("inventory"))

      wireThingsTogether()
    }
    targetX = nbt.getFloat("targetX")
    targetY = nbt.getFloat("targetY")
    targetZ = nbt.getFloat("targetZ")
    targetAcceleration = nbt.getFloat("targetAcceleration")
    setSelectedSlot(nbt.getByte("selectedSlot") & 0xFF)
    setSelectedTank(nbt.getByte("selectedTank") & 0xFF)
    statusText = nbt.getString("statusText")
    lightColor = nbt.getInt("lightColor")
    if (nbt.contains("owner")) {
      _ownerName = nbt.getString("owner")
    }
    if (nbt.contains("ownerUuid")) {
      try _ownerUUID = UUID.fromString(nbt.getString("ownerUuid")) catch {
        case _: IllegalArgumentException => // 保留默认 UUID。
      }
    }
  }

  override protected def addAdditionalSaveData(nbt: CompoundTag): Unit = {
    // 旧版在客户端直接 return；1.21.1 的 `save` 一般只在服务端调用，这里保留同样的保护。
    if (world.isClientSide) return
    components.saveComponents()
    info.storedEnergy = if (control != null && control.node.isInstanceOf[Connector]) {
      control.node.asInstanceOf[Connector].localBuffer.toInt
    } else 0
    val infoTag = new CompoundTag()
    info.save(infoTag)
    nbt.put("info", infoTag)
    if (machine != null) {
      val machineTag = new CompoundTag()
      machine.save(machineTag)
      nbt.put("machine", machineTag)
    }
    val componentsTag = new CompoundTag()
    components.save(componentsTag)
    nbt.put("components", componentsTag)
    val inventoryTag = new CompoundTag()
    mainInventory.save(inventoryTag)
    nbt.put("inventory", inventoryTag)
    nbt.putFloat("targetX", targetX)
    nbt.putFloat("targetY", targetY)
    nbt.putFloat("targetZ", targetZ)
    nbt.putFloat("targetAcceleration", targetAcceleration)
    nbt.putByte("selectedSlot", selectedSlot().toByte)
    nbt.putByte("selectedTank", _selectedTank.toByte)
    nbt.putString("statusText", statusText)
    nbt.putInt("lightColor", lightColor)
    nbt.putString("owner", _ownerName)
    nbt.putString("ownerUuid", _ownerUUID.toString)
  }
}

object Drone {
  /** 默认主人 UUID：与 1.7.10 的 `Settings.get.fakePlayerProfile.getId` 对应。 */
  val DefaultOwnerUUID: UUID = UUID.nameUUIDFromBytes("opencomputers_neo:drone".getBytes(StandardCharsets.UTF_8))

  /** 同步数据字段（原 `dataWatcher.addObject(2..12, ...)`）。
   *
   * 全部使用非原始类型，原因见 [[Drone#defineSynchedData]] 的注释。
   */
  val DataRunning: EntityDataAccessor[Integer] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.INT)
  val DataTargetX: EntityDataAccessor[java.lang.Float] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.FLOAT)
  val DataTargetY: EntityDataAccessor[java.lang.Float] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.FLOAT)
  val DataTargetZ: EntityDataAccessor[java.lang.Float] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.FLOAT)
  val DataTargetAcceleration: EntityDataAccessor[java.lang.Float] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.FLOAT)
  val DataSelectedSlot: EntityDataAccessor[Integer] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.INT)
  val DataGlobalBuffer: EntityDataAccessor[Integer] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.INT)
  val DataGlobalBufferSize: EntityDataAccessor[Integer] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.INT)
  val DataStatusText: EntityDataAccessor[String] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.STRING)
  val DataInventorySize: EntityDataAccessor[Integer] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.INT)
  val DataLightColor: EntityDataAccessor[Integer] =
    SynchedEntityData.defineId(classOf[Drone], EntityDataSerializers.INT)

  /** 合并两份流体堆叠（保持 `FluidStack` 不可变语义）。 */
  private def mergeFluid(a: FluidStack, b: FluidStack): FluidStack = {
    if (b == null || b.isEmpty) a
    else if (a == null || a.isEmpty) b
    else {
      val merged = a.copy()
      merged.setAmount(a.getAmount + b.getAmount)
      merged
    }
  }
}
