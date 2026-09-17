package li.cil.oc.common.tileentity

import li.cil.oc.server.{PacketSender => ServerPacketSender}
import java.util

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network._
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.common.Slot
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 充电器（原 1.7.10 `common.tileentity.Charger`）：按红石信号强度决定充电速率，
 * 给相邻机器人、玩家纳米机器、附近玩家的平板 / 可充电装备，以及自身槽位里的平板充电。
 *
 * 纹理：下/上 = ChargerTop，北 = ChargerFront，南 = ChargerBack，其它 = ChargerSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `updateEntity()` → [[li.cil.oc.common.tileentity.traits.TileEntity#tick]]；
 *    `world.getWorldInfo.getWorldTotalTime` → `world.getGameTime`；
 *    `world.rand` → `world.getRandom`。
 *  - `world.spawnParticle("happyVillager", x, y, z, ...)` → `Level#addParticle` +
 *    [[net.minecraft.core.particles.ParticleTypes]]；`Vec3#xCoord/yCoord/zCoord` → `x/y/z`。
 *  - `AABB#expand(1, 1, 1)` → `AABB#inflate(1, 1, 1)`；
 *    `world.getEntitiesWithinAABB(cls, aabb)` → `Level#getEntitiesOfClass`。
 *  - `player.addChatMessage(...)` → `player.displayClientMessage(..., false)`。
 *  - `player.inventory.mainInventory` → `player.getInventory.items`。
 *  - `getSizeInventory` → `getSlots`、`getInventoryStackLimit` → `getSlotLimit`、
 *    `isItemValidForSlot` → `isItemValid`；`stack.isEmpty` 取代 `stack == null`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）。
 *
 * 降级清单：
 *  - `integration.util.ItemCharge` 未移植 → 改为直接查询
 *    [[li.cil.oc.api.driver.item.Chargeable]] 物品驱动（见 [[chargeableDriver]]）。
 *  - `common.entity.Drone` 未纳入编译范围（`common/entity` 包尚未移植完成）→
 *    无人机充电暂时停用，见 [[updateConnectors]] 的 TODO。
 *  - `ServerPacketSender.sendChargerState(this)` → [[markBlockForUpdate]] + TODO。
 */
class Charger(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.PowerAcceptor with traits.RedstoneAware with traits.Rotatable
    with traits.ComponentInventory with Analyzable with traits.StateAware with DeviceInfo {

  val node = api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferConverter).
    create()

  val connectors = mutable.Set.empty[Chargeable]
  val equipment = mutable.Set.empty[ItemStack]

  var chargeSpeed = 0.0

  var hasPower = false

  var invertSignal = false

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Charger",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "PowerUpper"
  )

  // 1.7.10 的 `scala.collection.convert.WrapAsJava._` 提供隐式转换；
  // 1.21.1（Scala 2.13）改为显式 `.asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（只应由客户端渲染调用）。
  override def hasConnector(side: Direction): Boolean = side != facing

  override def connector(side: Direction): Option[Connector] = Option(if (side != facing) node else null)

  override def energyThroughput: Double = Settings.get.chargerRate

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    // TODO Refine to only report working if present robots/drones actually *need* power.
    if (connectors.nonEmpty) {
      if (hasPower) util.EnumSet.of(api.util.StateAware.State.IsWorking)
      else util.EnumSet.of(api.util.StateAware.State.CanWork)
    }
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    // 原 `player.addChatMessage(...)`；1.21.1 改用 `displayClientMessage`。
    player.displayClientMessage(Localization.Analyzer.ChargerSpeed(chargeSpeed), false)
    null
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = true

  // ----------------------------------------------------------------------- //
  // 可充电物品。原实现走未移植的 `integration.util.ItemCharge`（IMC 注册的反射方法），
  // 这里改为直接查询 `api.driver.item.Chargeable` 物品驱动，语义等价。
  // ----------------------------------------------------------------------- //

  private def chargeableDriver(stack: ItemStack): Option[api.driver.item.Chargeable] = {
    if (stack == null || stack.isEmpty) None
    else Option(api.Driver.driverFor(stack)).collect {
      case chargeable: api.driver.item.Chargeable => chargeable
    }
  }

  private def canCharge(stack: ItemStack): Boolean = chargeableDriver(stack).isDefined

  private def chargeItem(stack: ItemStack, amount: Double): Double = chargeableDriver(stack) match {
    case Some(chargeable) => chargeable.charge(stack, amount, false)
    case _ => 0.0
  }

  // ----------------------------------------------------------------------- //

  private def chargeStack(stack: ItemStack, charge: Double): Unit = {
    if (stack != null && !stack.isEmpty && charge > 0) {
      val offered = charge + node.changeBuffer(-charge)
      val surplus = chargeItem(stack, offered)
      node.changeBuffer(surplus)
    }
  }

  override def tick(): Unit = {
    super.tick()

    // Offset by hashcode to avoid all chargers ticking at the same time.
    if ((world.getGameTime + math.abs(hashCode())) % 20 == 0) {
      updateConnectors()
    }

    if (isServer && world.getGameTime % Settings.get.tickFrequency == 0) {
      var canCharge = Settings.get.ignorePower

      // Charging of external devices.
      {
        val charge = Settings.get.chargeRateExternal * chargeSpeed * Settings.get.tickFrequency
        canCharge ||= charge > 0 && node.globalBuffer >= charge * 0.5
        if (canCharge) {
          connectors.foreach(connector => node.changeBuffer(connector.changeBuffer(charge + node.changeBuffer(-charge))))
        }
      }

      // Charging of internal devices.
      {
        val charge = Settings.get.chargeRateTablet * chargeSpeed * Settings.get.tickFrequency
        canCharge ||= charge > 0 && node.globalBuffer >= charge * 0.5
        if (canCharge) {
          (0 until getSlots).map(getStackInSlot).foreach(chargeStack(_, charge))
        }
      }

      // Charging of equipment
      {
        val charge = Settings.get.chargeRateTablet * chargeSpeed * Settings.get.tickFrequency
        canCharge ||= charge > 0 && node.globalBuffer >= charge * 0.5
        if (canCharge) {
          equipment.foreach(chargeStack(_, charge))
        }
      }

      if (hasPower && !canCharge) {
        hasPower = false
        ServerPacketSender.sendChargerState(this)
      }
      if (!hasPower && canCharge) {
        hasPower = true
        ServerPacketSender.sendChargerState(this)
      }
    }

    if (isClient && chargeSpeed > 0 && hasPower && world.getGameTime % 10 == 0) {
      connectors.foreach(connector => {
        val connectorPos = connector.pos
        val theta = world.getRandom.nextDouble() * Math.PI
        val phi = world.getRandom.nextDouble() * Math.PI * 2
        val dx = 0.45 * Math.sin(theta) * Math.cos(phi)
        val dy = 0.45 * Math.sin(theta) * Math.sin(phi)
        val dz = 0.45 * Math.cos(theta)
        world.addParticle(ParticleTypes.HAPPY_VILLAGER,
          connectorPos.x + dx, connectorPos.y + dz, connectorPos.z + dy, 0, 0, 0)
      })
    }
  }

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      onNeighborChanged()
    }
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    chargeSpeed = nbt.getDouble("chargeSpeed") max 0 min 1
    hasPower = nbt.getBoolean("hasPower")
    invertSignal = nbt.getBoolean("invertSignal")
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putDouble("chargeSpeed", chargeSpeed)
    nbt.putBoolean("hasPower", hasPower)
    nbt.putBoolean("invertSignal", invertSignal)
  }

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解。
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    chargeSpeed = nbt.getDouble("chargeSpeed")
    hasPower = nbt.getBoolean("hasPower")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putDouble("chargeSpeed", chargeSpeed)
    nbt.putBoolean("hasPower", hasPower)
  }

  // ----------------------------------------------------------------------- //

  override def isComponentSlot(slot: Int, stack: ItemStack): Boolean =
    super.isComponentSlot(slot, stack) && (Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver) => driver.slot(stack) == Slot.Tablet
      case _ => false
    })

  override def getSlots: Int = 1

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (0, Some(driver)) if driver.slot(stack) == Slot.Tablet => true
    case _ => canCharge(stack)
  }

  // ----------------------------------------------------------------------- //

  override def updateRedstoneInput(side: Direction): Unit = {
    super.updateRedstoneInput(side)
    val signal = getInput.max min 15

    if (invertSignal) chargeSpeed = (15 - signal) / 15.0
    else chargeSpeed = signal / 15.0
    if (isServer) {
      // 把 chargeSpeed / hasPower 同步给客户端用于渲染（对齐 OCCE）。
      ServerPacketSender.sendChargerState(this)
    }
  }

  def onNeighborChanged(): Unit = {
    checkRedstoneInputChanged()
    updateConnectors()
  }

  def updateConnectors(): Unit = {
    val robots = Direction.values().map(side => {
      val blockPos = BlockPosition(this).offset(side)
      if (world.blockExists(blockPos)) Option(world.getTileEntity(blockPos))
      else None
    }).collect {
      case Some(t: RobotProxy) => new RobotChargeable(t.robot)
    }

    // TODO(common.entity.Drone): `common/entity/Drone.scala` 尚未纳入编译范围，无人机实体
    // 暂时无法枚举。移植完成后恢复为：
    //   val bounds = BlockPosition(this).bounds.inflate(1, 1, 1)
    //   val drones = world.getEntitiesOfClass(classOf[Drone], bounds).asScala.collect {
    //     case drone: Drone => new DroneChargeable(drone)
    //   }
    val drones = Array.empty[Chargeable]

    val bounds = BlockPosition(this).bounds.inflate(1, 1, 1)
    val players = world.getEntitiesOfClass(classOf[Player], bounds).asScala

    val chargeablePlayers = players.collect {
      case player if api.Nanomachines.hasController(player) => new PlayerChargeable(player)
    }

    // Only update list when we have to, keeps pointless block updates to a minimum.

    val newConnectors = robots ++ drones ++ chargeablePlayers
    if (connectors.size != newConnectors.length || (connectors.nonEmpty && (connectors -- newConnectors).nonEmpty)) {
      connectors.clear()
      connectors ++= newConnectors
      notifyNeighbors()
    }

    // scan players for chargeable equipment
    equipment.clear()
    players.foreach {
      player => player.getInventory.items.asScala.foreach {
        stack: ItemStack =>
          if (Option(Driver.driverFor(stack, getClass)) match {
            case Some(driver) if driver.slot(stack) == Slot.Tablet => true
            case _ => canCharge(stack)
          }) {
            equipment += stack
          }
      }
    }
  }

  trait Chargeable {
    def pos: Vec3

    def changeBuffer(delta: Double): Double
  }

  abstract class ConnectorChargeable(val connector: Connector) extends Chargeable {
    override def changeBuffer(delta: Double): Double = connector.changeBuffer(delta)

    override def equals(obj: scala.Any): Boolean = obj match {
      case chargeable: ConnectorChargeable => chargeable.connector == connector
      case _ => false
    }
  }

  class RobotChargeable(val robot: Robot) extends ConnectorChargeable(robot.node.asInstanceOf[Connector]) {
    override def pos: Vec3 = BlockPosition(robot).toVec3

    override def equals(obj: scala.Any): Boolean = obj match {
      case chargeable: RobotChargeable => chargeable.robot == robot
      case _ => false
    }

    override def hashCode(): Int = robot.hashCode()
  }

  /**
   * 无人机充电入口。
   *
   * TODO(common.entity.Drone): 原实现为
   * `class DroneChargeable(val drone: Drone) extends ConnectorChargeable(drone.components.node.asInstanceOf[Connector])`。
   * `common/entity` 包尚未纳入编译范围，因此这里不引用 `Drone`；
   * 移植完成后把本类恢复，并在 [[updateConnectors]] 里重新枚举无人机实体。
   */

  class PlayerChargeable(val player: Player) extends Chargeable {
    override def pos: Vec3 = new Vec3(player.getX, player.getY, player.getZ)

    override def changeBuffer(delta: Double): Double = {
      api.Nanomachines.getController(player) match {
        case controller: Controller => controller.changeBuffer(delta)
        case _ => delta // Cannot charge.
      }
    }

    override def equals(obj: scala.Any): Boolean = obj match {
      case chargeable: PlayerChargeable => chargeable.player == player
      case _ => false
    }

    override def hashCode(): Int = player.hashCode()
  }
}
