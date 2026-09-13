package li.cil.oc.common.tileentity.traits

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.api.internal
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util.RotationHelper
import net.minecraft.world.entity.Entity
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction

/** BlockEntity base class for rotatable blocks. */
trait Rotatable extends RotationAware with internal.Rotatable {
  // ----------------------------------------------------------------------- //
  // Lookup tables
  // ----------------------------------------------------------------------- //

  private val pitch2Direction = Array(Direction.UP, Direction.NORTH, Direction.DOWN)

  private val yaw2Direction = Array(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST)

  // ----------------------------------------------------------------------- //
  // State
  // ----------------------------------------------------------------------- //

  /** One of Up, Down and North (where north means forward/no pitch). */
  private var _pitch = Direction.NORTH

  /** One of the four cardinal directions. */
  private var _yaw = Direction.SOUTH

  // ----------------------------------------------------------------------- //
  // Accessors
  // ----------------------------------------------------------------------- //

  def pitch = _pitch

  def pitch_=(value: Direction): Unit =
    trySetPitchYaw(value match {
      case Direction.DOWN | Direction.UP => value
      case _ => Direction.NORTH
    }, _yaw)

  def yaw = _yaw

  def yaw_=(value: Direction): Unit =
    trySetPitchYaw(pitch, value match {
      case Direction.DOWN | Direction.UP => _yaw
      case _ => value
    })

  def setFromEntityPitchAndYaw(entity: Entity) =
    trySetPitchYaw(
      pitch2Direction((entity.rotationPitch / 90).round + 1),
      yaw2Direction((entity.rotationYaw / 360 * 4).round & 3))

  def setFromFacing(value: Direction) =
    value match {
      case Direction.DOWN | Direction.UP =>
        trySetPitchYaw(value, yaw)
      case yaw =>
        trySetPitchYaw(Direction.NORTH, yaw)
    }

  def invertRotation() =
    trySetPitchYaw(_pitch match {
      case Direction.DOWN | Direction.UP => _pitch.getOpposite
      case _ => Direction.NORTH
    }, _yaw.getOpposite)

  override def facing = _pitch match {
    case Direction.DOWN | Direction.UP => _pitch
    case _ => _yaw
  }

  def rotate(axis: Direction) = {
    val block = world.getBlock(position)
    if (block != null) {
      val valid = block.getValidRotations(world, x, y, z)
      if (valid != null && valid.contains(axis)) {
        val (newPitch, newYaw) = facing.getRotation(axis) match {
          case value@(Direction.UP | Direction.DOWN) =>
            if (value == pitch) (value, yaw.getRotation(axis))
            else (value, yaw)
          case value => (Direction.NORTH, value)
        }
        trySetPitchYaw(newPitch, newYaw)
      }
      else false
    }
    else false
  }

  override def toLocal(value: Direction) = RotationHelper.toLocal(_pitch, _yaw, value)

  override def toGlobal(value: Direction) = RotationHelper.toGlobal(_pitch, _yaw, value)

  def validFacings = Array(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)

  // ----------------------------------------------------------------------- //

  protected def onRotationChanged(): Unit = {
    if (isServer) {
      ServerPacketSender.sendRotatableState(this)
    }
    else {
      world.markBlockForUpdate(x, y, z)
    }
    world.notifyBlocksOfNeighborChange(x, y, z, block)
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag) = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "pitch")) {
      pitch = Direction.getOrientation(nbt.getInteger(Settings.namespace + "pitch"))
    }
    if (nbt.contains(Settings.namespace + "yaw")) {
      yaw = Direction.getOrientation(nbt.getInteger(Settings.namespace + "yaw"))
    }
    validatePitchAndYaw()
    updateTranslation()
  }

  override def writeToNBTForServer(nbt: CompoundTag) = {
    super.writeToNBTForServer(nbt)
    nbt.putInt(Settings.namespace + "pitch", pitch.ordinal)
    nbt.putInt(Settings.namespace + "yaw", yaw.ordinal)
  }

  @SideOnly(Dist.CLIENT)
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    pitch = Direction.getOrientation(nbt.getInteger("pitch"))
    yaw = Direction.getOrientation(nbt.getInteger("yaw"))
    validatePitchAndYaw()
    updateTranslation()
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putInt("pitch", pitch.ordinal)
    nbt.putInt("yaw", yaw.ordinal)
  }

  private def validatePitchAndYaw(): Unit = {
    if (!Set(Direction.UP, Direction.DOWN, Direction.NORTH).contains(_pitch)) {
      _pitch = Direction.NORTH
    }
    if (!Set(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST).contains(_yaw)) {
      _yaw = Direction.SOUTH
    }
  }

  // ----------------------------------------------------------------------- //

  /** Updates cached translation array and sends notification to clients. */
  private def updateTranslation() = {
    if (world != null) {
      onRotationChanged()
    }
  }

  /** Validates new values against the allowed rotations as set in our block. */
  private def trySetPitchYaw(pitch: Direction, yaw: Direction) = {
    var changed = false
    if (pitch != _pitch) {
      changed = true
      _pitch = pitch
    }
    if (yaw != _yaw) {
      changed = true
      _yaw = yaw
    }
    if (changed) {
      updateTranslation()
    }
    changed
  }
}
