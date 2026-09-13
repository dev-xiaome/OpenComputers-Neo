package li.cil.oc.common.tileentity.traits

import net.minecraft.core.Direction

/** 记录方块朝向换算能力的基础 trait（对应 1.7.10 的 `traits.RotationAware`）。 */
trait RotationAware extends TileEntity {
  // 注意：Scala 的自类型不会被继承，TileEntity 的子 trait 必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  def toLocal(value: Direction): Direction = value

  def toGlobal(value: Direction): Direction = value
}
