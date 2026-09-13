package li.cil.oc.util

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

import scala.language.implicitConversions

/**
 * `AABB` 的扩展（体积 / 表面积 / 绕 Y 轴旋转）。
 *
 * 1.21.1 迁移要点：
 *  - `AABB.getBoundingBox(...)` → `new AABB(...)`
 *  - `Vec3` 变为不可变类型：`rotateAroundY(a)` 改为 `yRot(a)` 并接收返回值
 *  - `xCoord/yCoord/zCoord` → `x/y/z`
 */
object ExtendedAABB {
  implicit def extendedAABB(bounds: AABB): ExtendedAABB = new ExtendedAABB(bounds)

  def unitBounds = new AABB(0, 0, 0, 1, 1, 1)

  class ExtendedAABB(val bounds: AABB) {
    def volume: Int = {
      val sx = ((bounds.maxX - bounds.minX) * 16).round.toInt
      val sy = ((bounds.maxY - bounds.minY) * 16).round.toInt
      val sz = ((bounds.maxZ - bounds.minZ) * 16).round.toInt
      sx * sy * sz
    }

    def surface: Int = {
      val sx = ((bounds.maxX - bounds.minX) * 16).round.toInt
      val sy = ((bounds.maxY - bounds.minY) * 16).round.toInt
      val sz = ((bounds.maxZ - bounds.minZ) * 16).round.toInt
      sx * sy * 2 + sx * sz * 2 + sy * sz * 2
    }

    def rotateTowards(facing: Direction) = rotateY(facing match {
      case Direction.WEST => 3
      case Direction.NORTH => 2
      case Direction.EAST => 1
      case _ => 0
    })

    def rotateY(count: Int): AABB = {
      val angle = count * Math.PI.toFloat * 0.5f
      // 1.21.1 的 Vec3 不可变，旋转需要接收新实例。
      val min = new Vec3(bounds.minX - 0.5, bounds.minY - 0.5, bounds.minZ - 0.5).yRot(angle)
      val max = new Vec3(bounds.maxX - 0.5, bounds.maxY - 0.5, bounds.maxZ - 0.5).yRot(angle)
      new AABB(
        (math.min(min.x + 0.5, max.x + 0.5) * 32).round / 32f,
        (math.min(min.y + 0.5, max.y + 0.5) * 32).round / 32f,
        (math.min(min.z + 0.5, max.z + 0.5) * 32).round / 32f,
        (math.max(min.x + 0.5, max.x + 0.5) * 32).round / 32f,
        (math.max(min.y + 0.5, max.y + 0.5) * 32).round / 32f,
        (math.max(min.z + 0.5, max.z + 0.5) * 32).round / 32f)
    }
  }

}
