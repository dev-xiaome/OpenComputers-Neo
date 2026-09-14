package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.internal
import li.cil.oc.util.RotationHelper
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * 可旋转方块的方块实体基 trait（对应 1.7.10 的 `traits.Rotatable`）。
 *
 * 朝向由 `pitch`（UP / DOWN / NORTH，其中 NORTH 表示「无俯仰」）与 `yaw`（水平四向）两段组成，
 * 与原实现一致；`facing` 把两者合成一个 [[Direction]]。
 *
 * 1.21.1 迁移要点：
 *  - `ForgeDirection` → `Direction`；`Direction.UNKNOWN` 不存在，默认朝向用 `NORTH`。
 *  - `entity.rotationPitch` / `rotationYaw` → `entity.getXRot` / `entity.getYRot`。
 *  - `entity.getRotation(axis)`（Forge 扩展）在 1.21.1 没有等价物，见 [[rotation]]。
 *  - `world.getBlock(position)` → `world.getBlockState(pos).getBlock`；OC 的自定义旋转合法性
 *    查询改走 [[li.cil.oc.common.block.SimpleBlockHooks.validRotations]]（原 `Block#getValidRotations`）。
 *  - 客户端同步：原为 `ServerPacketSender.sendRotatableState(this)`，`server` 包未移植，
 *    这里退化为方块更新（同步标签里会带上 pitch/yaw）。
 */
trait Rotatable extends RotationAware with internal.Rotatable {
  // 注意：Scala 的自类型不会被继承，TileEntity 的子 trait 必须重新声明。
  self: BlockEntity =>

  // ----------------------------------------------------------------------- //
  // 查表
  // ----------------------------------------------------------------------- //

  private val pitch2Direction = Array(Direction.UP, Direction.NORTH, Direction.DOWN)

  private val yaw2Direction = Array(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST)

  // ----------------------------------------------------------------------- //
  // 状态
  // ----------------------------------------------------------------------- //

  /** UP、DOWN 或 NORTH（NORTH 表示水平朝向/无俯仰）。 */
  private var _pitch = Direction.NORTH

  /** 四个水平方向之一。 */
  private var _yaw = Direction.SOUTH

  // ----------------------------------------------------------------------- //
  // 访问器
  // ----------------------------------------------------------------------- //

  def pitch: Direction = _pitch

  def pitch_=(value: Direction): Unit =
    trySetPitchYaw(value match {
      case Direction.DOWN | Direction.UP => value
      case _ => Direction.NORTH
    }, _yaw)

  def yaw: Direction = _yaw

  def yaw_=(value: Direction): Unit =
    trySetPitchYaw(pitch, value match {
      case Direction.DOWN | Direction.UP => _yaw
      case _ => value
    })

  def setFromEntityPitchAndYaw(entity: Entity): Boolean =
    trySetPitchYaw(
      pitch2Direction((entity.getXRot / 90).round + 1),
      yaw2Direction((entity.getYRot / 360 * 4).round & 3))

  def setFromFacing(value: Direction): Boolean =
    value match {
      case Direction.DOWN | Direction.UP =>
        trySetPitchYaw(value, yaw)
      case yaw =>
        trySetPitchYaw(Direction.NORTH, yaw)
    }

  def invertRotation(): Boolean =
    trySetPitchYaw(_pitch match {
      case Direction.DOWN | Direction.UP => _pitch.getOpposite
      case _ => Direction.NORTH
    }, _yaw.getOpposite)

  override def facing: Direction = _pitch match {
    case Direction.DOWN | Direction.UP => _pitch
    case _ => _yaw
  }

  def rotate(axis: Direction): Boolean = {
    if (world == null) return false
    val block = world.getBlockState(position.toChunkCoordinates).getBlock
    if (block != null) {
      val valid = block match {
        case simple: li.cil.oc.common.block.SimpleBlockHooks => simple.validRotations
        case _ => null
      }
      if (valid != null && valid.contains(axis)) {
        val (newPitch, newYaw) = rotation(facing, axis) match {
          case value@(Direction.UP | Direction.DOWN) =>
            if (value == pitch) (value, rotation(yaw, axis))
            else (value, yaw)
          case value => (Direction.NORTH, value)
        }
        trySetPitchYaw(newPitch, newYaw)
      }
      else false
    }
    else false
  }

  override def toLocal(value: Direction): Direction = RotationHelper.toLocal(_pitch, _yaw, value)

  override def toGlobal(value: Direction): Direction = RotationHelper.toGlobal(_pitch, _yaw, value)

  def validFacings: Array[Direction] = Array(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)

  // ----------------------------------------------------------------------- //

  protected def onRotationChanged(): Unit = {
    if (isServer) {
      // TODO(server.PacketSender): 原为 PacketSender.sendRotatableState(this)。
      markBlockForUpdate()
    }
    else {
      markBlockForUpdate()
    }
    notifyNeighbors()
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "pitch")) {
      pitch = Direction.from3DDataValue(nbt.getInt(Settings.namespace + "pitch"))
    }
    if (nbt.contains(Settings.namespace + "yaw")) {
      yaw = Direction.from3DDataValue(nbt.getInt(Settings.namespace + "yaw"))
    }
    validatePitchAndYaw()
    updateTranslation()
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putInt(Settings.namespace + "pitch", pitch.ordinal)
    nbt.putInt(Settings.namespace + "yaw", yaw.ordinal)
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    pitch = Direction.from3DDataValue(nbt.getInt("pitch"))
    yaw = Direction.from3DDataValue(nbt.getInt("yaw"))
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

  /**
   * 1.7.10 `ForgeDirection#getRotation(axis)` 的替代实现。
   *
   * Forge 的该方法语义是「把方向绕给定轴旋转 90°」，1.21.1 的原版 `Direction` 没有这个 API，
   * 而 OC 的方块（机箱、屏幕、磁盘驱动器等）只允许绕 **竖直轴** 旋转
   * （`validRotations` 为 UP / DOWN），因此这里只实现这一种情况：
   *  - 竖直轴 + 水平方向：按 UP 顺时针 / DOWN 逆时针在四个水平方向间循环；
   *  - 其它组合：保持不变。
   *
   * TODO(integration): 扳手集成移植时，如果发现 1.7.10 的旋转方向与此相反，
   * 只需把下面的 `+1` / `-1` 对调即可。
   */
  private def rotation(dir: Direction, axis: Direction): Direction = {
    if (!dir.getAxis.isHorizontal) return dir
    val horizontal = Array(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
    val index = horizontal.indexOf(dir)
    if (index < 0) return dir
    axis match {
      case Direction.UP => horizontal((index + 1) & 3)
      case Direction.DOWN => horizontal((index + 3) & 3)
      case _ => dir
    }
  }

  /** 更新缓存的翻译表并通知客户端（原 `updateTranslation`）。 */
  private def updateTranslation(): Unit = {
    if (world != null) {
      onRotationChanged()
    }
  }

  /** 校验新值并把变化通知出去（原 `trySetPitchYaw`）。 */
  private def trySetPitchYaw(pitch: Direction, yaw: Direction): Boolean = {
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
