package li.cil.oc.common.block.property

import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.properties.{BlockStateProperties, DirectionProperty}

object PropertyRotatable {
  final val Facing = BlockStateProperties.HORIZONTAL_FACING
  final val Mount = DirectionProperty.create("mount", (d: Direction) => d == Direction.UP || d == Direction.DOWN)
  final val Pitch = DirectionProperty.create("pitch", (d: Direction) => d.getAxis == Direction.Axis.Y || d == Direction.NORTH)
  final val Yaw = DirectionProperty.create("yaw", Direction.Plane.HORIZONTAL)
}
