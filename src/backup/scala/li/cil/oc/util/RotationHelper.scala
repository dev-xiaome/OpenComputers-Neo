package li.cil.oc.util

import net.minecraft.core.Direction

import scala.collection.mutable

/**
 * 按方块朝向（pitch + yaw）做方向换算的工具。
 *
 * 1.21.1 迁移要点：
 *  - `Direction.UNKNOWN` 已不存在（新版只有 6 个方向），所以翻译表的每行由
 *    7 项缩减为 6 项；由于 `Direction.ordinal` 只可能是 0..5，旧表里那个
 *    `unknown` 项本来也取不到，因此缩减后行为完全一致
 *  - `Direction.getOrientation(i)` → `Direction.from3DDataValue(i)`
 *  - `direction.ordinal` 在 1.21.1 的 `Direction` 中顺序仍为
 *    DOWN/UP/NORTH/SOUTH/WEST/EAST，故 `ordinal - 2` 的用法保持不变
 *  - 翻译表是 6 个方向上的一个置换（每行没有重复项），`ordinal` 又天然落在
 *    0..5，所以 `toLocal`/`toGlobal` 不需要返回 `Option`：
 *    `li.cil.oc.api.internal.Rotatable` 与各调用方都要求 `Direction`
 */
object RotationHelper {
  def fromYaw(yaw: Float): Direction = {
    (yaw / 360 * 4).round & 3 match {
      case 0 => Direction.SOUTH
      case 1 => Direction.WEST
      case 2 => Direction.NORTH
      case 3 => Direction.EAST
    }
  }

  def toLocal(pitch: Direction, yaw: Direction, value: Direction): Direction =
    translationFor(pitch, yaw)(value.ordinal)

  def toGlobal(pitch: Direction, yaw: Direction, value: Direction): Direction =
    inverseTranslationFor(pitch, yaw)(value.ordinal)

  def translationFor(pitch: Direction, yaw: Direction): Array[Direction] =
    translationCache.synchronized(translationCache.
      getOrElseUpdate(pitch, mutable.Map.empty).
      getOrElseUpdate(yaw, translations(pitch.ordinal)(yawIndex(yaw))))

  /**
   * 翻译表是 `Direction.ordinal` 上的一个置换，所以逆表可以直接由正向表反推：
   * 正向表在索引 `i` 处是 `t(i)`，逆表就在 `t(i)` 处放 `i`。
   *
   * 1.7.10 的写法是 `t.indices.map(getOrientation).map(t.indexOf).map(getOrientation)`，
   * 表里有 `UNKNOWN` 项时 `indexOf` 会返回 -1；1.21.1 没有 `UNKNOWN`，
   * 且表是完整置换，故这里用数组直接填充，结果与旧实现一致。
   */
  def inverseTranslationFor(pitch: Direction, yaw: Direction): Array[Direction] =
    inverseTranslationCache.synchronized(inverseTranslationCache.
      getOrElseUpdate(pitch, mutable.Map.empty).
      getOrElseUpdate(yaw, {
        val translation = translationFor(pitch, yaw)
        val inverse = new Array[Direction](translation.length)
        for (i <- translation.indices) {
          inverse(translation(i).ordinal) = Direction.from3DDataValue(i)
        }
        inverse
      }))

  // ----------------------------------------------------------------------- //

  /** 把四种水平朝向的 ordinal（NORTH=2…EAST=5）映射到翻译表的 0..3。 */
  private def yawIndex(yaw: Direction): Int = (yaw.ordinal - 2) & 3

  private val translationCache = mutable.Map.empty[Direction, mutable.Map[Direction, Array[Direction]]]
  private val inverseTranslationCache = mutable.Map.empty[Direction, mutable.Map[Direction, Array[Direction]]]

  /**
   * Translates directions based on the block's pitch and yaw. The base
   * forward direction is facing south with no pitch. The outer array is for
   * the three different pitch states, the inner for the four different yaw
   * states.
   */
  private val translations: Array[Array[Array[Direction]]] = Array(
    // Pitch = Down
    Array(
      // Yaw = North
      Array(D.south, D.north, D.up, D.down, D.east, D.west),
      // Yaw = South
      Array(D.south, D.north, D.down, D.up, D.west, D.east),
      // Yaw = West
      Array(D.south, D.north, D.west, D.east, D.up, D.down),
      // Yaw = East
      Array(D.south, D.north, D.east, D.west, D.down, D.up)),
    // Pitch = Up
    Array(
      // Yaw = North
      Array(D.north, D.south, D.down, D.up, D.east, D.west),
      // Yaw = South
      Array(D.north, D.south, D.up, D.down, D.west, D.east),
      // Yaw = West
      Array(D.north, D.south, D.west, D.east, D.down, D.up),
      // Yaw = East
      Array(D.north, D.south, D.east, D.west, D.up, D.down)),
    // Pitch = Forward (North|East|South|West)
    Array(
      // Yaw = North
      Array(D.down, D.up, D.south, D.north, D.east, D.west),
      // Yaw = South
      Array(D.down, D.up, D.north, D.south, D.west, D.east),
      // Yaw = West
      Array(D.down, D.up, D.west, D.east, D.south, D.north),
      // Yaw = East
      Array(D.down, D.up, D.east, D.west, D.north, D.south)))

  /** Shortcuts for directions to make the above more readable. */
  private object D {
    val down = Direction.DOWN
    val up = Direction.UP
    val north = Direction.NORTH
    val south = Direction.SOUTH
    val west = Direction.WEST
    val east = Direction.EAST
  }

}
