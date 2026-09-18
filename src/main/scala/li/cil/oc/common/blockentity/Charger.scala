package li.cil.oc.common.blockentity

import java.util
import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.api.network._
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.Slot
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.menu
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.entity.Drone
import li.cil.oc.integration.util.ItemCharge
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.util.ExtendedLevel._
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.MenuProvider
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.{CompoundTag => CompoundNBT}
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.Util
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class Charger(pos: BlockPos, state: BlockState)
  extends BlockEntity(BlockEntityTypes.CHARGER.get(), pos, state) with traits.Environment with traits.PowerAcceptor with traits.RedstoneAware
  with traits.Rotatable with traits.ComponentInventory with traits.Tickable with Analyzable with traits.StateAware with DeviceInfo with MenuProvider
    with IBlockEntityExtension {

  val node: Connector = api.Network.newNode(this, Visibility.None).
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

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  override protected def hasConnector(side: Direction): Boolean = side != facing

  override protected def connector(side: Direction) = Option(if (side != facing) node else null)

  override def energyThroughput: Double = Settings.get.chargerRate

  override def getCurrentState: util.EnumSet[StateAware.State] = {
    // TODO Refine to only report working if present robots/drones actually *need* power.
    if (connectors.nonEmpty) {
      if (hasPower) util.EnumSet.of(api.util.StateAware.State.IsWorking)
      else util.EnumSet.of(api.util.StateAware.State.CanWork)
    }
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  override def onAnalyze(player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Null = {
    player.sendSystemMessage(Localization.Analyzer.ChargerSpeed(chargeSpeed))
    null
  }

  // ----------------------------------------------------------------------- //

  private def chargeStack(stack: ItemStack, charge: Double): Unit = {
    if (!stack.isEmpty && charge > 0) {
      val missing = node.changeBuffer(-charge)
      val surplus = ItemCharge.charge(stack, charge + missing) // missing is negative
      node.changeBuffer(surplus)
    }
  }

  override def updateEntity(): Unit = {
    super.updateEntity()

    // Offset by hashcode to avoid all chargers ticking at the same time.
    if ((getLevel.getLevelData.getGameTime + math.abs(hashCode())) % 20 == 0) {
      updateConnectors()
    }

    if (isServer && getLevel.getLevelData.getGameTime % Settings.get.tickFrequency == 0) {
      var canCharge = Settings.get.ignorePower

      // Charging of external devices.
      {
        val charge = Settings.get.chargeRateExternal * chargeSpeed * Settings.get.tickFrequency
        canCharge ||= charge > 0 && node.globalBuffer >= charge * 0.5
        if (canCharge) {
          connectors.foreach(connector => {
            val missing = node.changeBuffer(-charge)
            val surplus = connector.changeBuffer(charge + missing) // missing is negative
            node.changeBuffer(surplus)
          })
        }
      }

      // Charging of internal devices.
      {
        val charge = Settings.get.chargeRateTablet * chargeSpeed * Settings.get.tickFrequency
        canCharge ||= charge > 0 && node.globalBuffer >= charge * 0.5
        if (canCharge) {
          (0 until getContainerSize).map(getItem).foreach(chargeStack(_, charge))
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

    if (isClient && chargeSpeed > 0 && hasPower && getLevel.getLevelData.getGameTime % 10 == 0) {
      connectors.foreach(connector => {
        val position = connector.pos
        val theta = getLevel.random.nextDouble * Math.PI
        val phi = getLevel.random.nextDouble * Math.PI * 2
        val dx = 0.45 * Math.sin(theta) * Math.cos(phi)
        val dy = 0.45 * Math.sin(theta) * Math.sin(phi)
        val dz = 0.45 * Math.cos(theta)
        getLevel.addParticle(ParticleTypes.HAPPY_VILLAGER, position.x + dx, position.y + dz, position.z + dy, 0, 0, 0)
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

  override def loadComponentsCommon(holder: DataComponentHolder): Unit = {
    super.loadComponentsCommon(holder)
    chargeSpeed = holder.getComponent(OCComponents.CHARGE_SPEED) getOrElse 0.0 max 0.0 min 1.0
    hasPower = holder.getComponent(OCComponents.IS_POWERED) getOrElse false
  }

  override def saveComponentsCommon(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsCommon(holder)
    holder.setComponent(OCComponents.CHARGE_SPEED, chargeSpeed)
    holder.setComponent(OCComponents.IS_POWERED, hasPower)
  }

  override def loadComponentsForServer(holder: DataComponentHolder): Unit = {
    super.loadComponentsForServer(holder)
    invertSignal = holder.has(OCComponents.INVERT_SIGNAL)
  }

  override def saveComponentsForServer(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForServer(holder)
    holder.setComponent(OCComponents.INVERT_SIGNAL, invertSignal)
  }

  // ----------------------------------------------------------------------- //

  override def isComponentSlot(slot: Int, stack: ItemStack): Boolean =
    super.isComponentSlot(slot, stack) && (Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver) => driver.slot(stack) == Slot.Tablet
      case _ => false
    })

  override def getContainerSize = 1

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (0, Some(driver)) if driver.slot(stack) == Slot.Tablet => true
    case _ => ItemCharge.canCharge(stack)
  }

  // ----------------------------------------------------------------------- //

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new menu.Charger(id, playerInventory, this)

  // ----------------------------------------------------------------------- //

  override def updateRedstoneInput(side: Direction): Unit = {
    super.updateRedstoneInput(side)
    val signal = getInput.max min 15

    if (invertSignal) chargeSpeed = (15 - signal) / 15.0
    else chargeSpeed = signal / 15.0
    if (isServer) {
      ServerPacketSender.sendChargerState(this)
    }
  }

  def onNeighborChanged(): Unit = {
    checkRedstoneInputChanged()
    updateConnectors()
  }

  def updateConnectors(): Unit = {
    val robots = Direction.values.map(side => {
      val blockPos = BlockPosition(this).offset(side)
      if (getLevel.blockExists(blockPos)) Option(getLevel.getBlockEntity(blockPos))
      else None
    }).collect {
      case Some(t: RobotProxy) => new RobotChargeable(t.robot)
    }
    val bounds = BlockPosition(this).bounds.inflate(1, 1, 1)
    val drones = getLevel.getEntitiesOfClass(classOf[Drone], bounds).asScala.collect {
      case drone: Drone => new DroneChargeable(drone)
    }

    val players = getLevel.getEntitiesOfClass(classOf[Player], bounds).asScala.collect {
      case player: Player => player
    }

    val chargeablePlayers = players.collect {
      case player if api.Nanomachines.hasController(player) => new PlayerChargeable(player)
    }

    // Only update list when we have to, keeps pointless block updates to a minimum.

    val newConnectors = robots ++ drones ++ chargeablePlayers
    if (connectors.size != newConnectors.length || (connectors.nonEmpty && connectors.exists(c => !newConnectors.contains(c)))) {
      connectors.clear()
      connectors ++= newConnectors
      getLevel.updateNeighborsAt(getBlockPos, getBlockState.getBlock)
    }

    // scan players for chargeable equipment
    equipment.clear()
    players.foreach {
      player => player.inventory.items.asScala.foreach {
        stack =>
          if (Option(Driver.driverFor(stack, getClass)) match {
            case Some(driver) if driver.slot(stack) == Slot.Tablet => true
            case _ => ItemCharge.canCharge(stack)
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

  class DroneChargeable(val drone: Drone) extends ConnectorChargeable(drone.components.node.asInstanceOf[Connector]) {
    override def pos: Vec3 = new Vec3(drone.getX, drone.getY, drone.getZ)

    override def equals(obj: scala.Any): Boolean = obj match {
      case chargeable: DroneChargeable => chargeable.drone == drone
      case _ => false
    }

    override def hashCode(): Int = drone.hashCode()
  }

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
