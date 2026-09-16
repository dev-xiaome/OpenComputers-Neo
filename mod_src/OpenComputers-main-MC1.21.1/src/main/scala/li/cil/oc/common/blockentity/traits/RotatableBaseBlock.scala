package li.cil.oc.common.blockentity.traits

import li.cil.oc.Settings
import net.minecraft.core.{Direction, HolderLookup}
import net.minecraft.nbt.CompoundTag

/**
 * Like Rotatable, but stores the rotation information in the TE's NBT instead
 * of the block's metadata.
 */
trait RotatableBaseBlock extends Rotatable {
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

  override def pitch: Direction = _pitch

  override def yaw: Direction = _yaw

  // ----------------------------------------------------------------------- //

  private final val PitchTag = Settings.namespace + "pitch"
  private final val YawTag = Settings.namespace + "yaw"

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider) = {
    super.loadForServer(nbt, provider)
    if (nbt.contains(PitchTag)) {
      pitch = Direction.from3DDataValue(nbt.getInt(PitchTag))
    }
    if (nbt.contains(YawTag)) {
      yaw = Direction.from3DDataValue(nbt.getInt(YawTag))
    }
    validatePitchAndYaw()
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider) = {
    super.saveForServer(nbt, provider)
    nbt.putInt(PitchTag, pitch.ordinal)
    nbt.putInt(YawTag, yaw.ordinal)
  }

  override def loadForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForClient(nbt, provider)
    pitch = Direction.from3DDataValue(nbt.getInt(PitchTag))
    yaw = Direction.from3DDataValue(nbt.getInt(YawTag))
    validatePitchAndYaw()
  }

  override def saveForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForClient(nbt, provider)
    nbt.putInt(PitchTag, pitch.ordinal)
    nbt.putInt(YawTag, yaw.ordinal)
  }

  private def validatePitchAndYaw(): Unit = {
    if (!_pitch.getAxis.isVertical) {
      _pitch = Direction.NORTH
    }
    if (!_yaw.getAxis.isHorizontal) {
      _yaw = Direction.SOUTH
    }
    updateTranslation()
  }

  // ----------------------------------------------------------------------- //

  /** Validates new values against the allowed rotations as set in our block. */
  override protected def trySetPitchYaw(pitch: Direction, yaw: Direction) = {
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
