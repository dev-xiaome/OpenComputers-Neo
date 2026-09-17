package net.minecraft.world.entity.vehicle;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DismountHelper {
    public static int[][] offsetsForDirection(Direction p_direction) {
        Direction direction = p_direction.getClockWise();
        Direction direction1 = direction.getOpposite();
        Direction direction2 = p_direction.getOpposite();
        return new int[][]{
            {direction.getStepX(), direction.getStepZ()},
            {direction1.getStepX(), direction1.getStepZ()},
            {direction2.getStepX() + direction.getStepX(), direction2.getStepZ() + direction.getStepZ()},
            {direction2.getStepX() + direction1.getStepX(), direction2.getStepZ() + direction1.getStepZ()},
            {p_direction.getStepX() + direction.getStepX(), p_direction.getStepZ() + direction.getStepZ()},
            {p_direction.getStepX() + direction1.getStepX(), p_direction.getStepZ() + direction1.getStepZ()},
            {direction2.getStepX(), direction2.getStepZ()},
            {p_direction.getStepX(), p_direction.getStepZ()}
        };
    }

    public static boolean isBlockFloorValid(double distance) {
        return !Double.isInfinite(distance) && distance < 1.0;
    }

    public static boolean canDismountTo(CollisionGetter level, LivingEntity passenger, AABB boundingBox) {
        for (VoxelShape voxelshape : level.getBlockCollisions(passenger, boundingBox)) {
            if (!voxelshape.isEmpty()) {
                return false;
            }
        }

        return level.getWorldBorder().isWithinBounds(boundingBox);
    }

    public static boolean canDismountTo(CollisionGetter level, Vec3 offset, LivingEntity passenger, Pose pose) {
        return canDismountTo(level, passenger, passenger.getLocalBoundsForPose(pose).move(offset));
    }

    public static VoxelShape nonClimbableShape(BlockGetter level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);
        return !blockstate.is(BlockTags.CLIMBABLE) && (!(blockstate.getBlock() instanceof TrapDoorBlock) || !blockstate.getValue(TrapDoorBlock.OPEN))
            ? blockstate.getCollisionShape(level, pos)
            : Shapes.empty();
    }

    public static double findCeilingFrom(BlockPos pos, int ceiling, Function<BlockPos, VoxelShape> shapeForPos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();
        int i = 0;

        while (i < ceiling) {
            VoxelShape voxelshape = shapeForPos.apply(blockpos$mutableblockpos);
            if (!voxelshape.isEmpty()) {
                return (double)(pos.getY() + i) + voxelshape.min(Direction.Axis.Y);
            }

            i++;
            blockpos$mutableblockpos.move(Direction.UP);
        }

        return Double.POSITIVE_INFINITY;
    }

    @Nullable
    public static Vec3 findSafeDismountLocation(EntityType<?> entityType, CollisionGetter level, BlockPos pos, boolean onlySafePositions) {
        if (onlySafePositions && entityType.isBlockDangerous(level.getBlockState(pos))) {
            return null;
        } else {
            double d0 = level.getBlockFloorHeight(nonClimbableShape(level, pos), () -> nonClimbableShape(level, pos.below()));
            if (!isBlockFloorValid(d0)) {
                return null;
            } else if (onlySafePositions && d0 <= 0.0 && entityType.isBlockDangerous(level.getBlockState(pos.below()))) {
                return null;
            } else {
                Vec3 vec3 = Vec3.upFromBottomCenterOf(pos, d0);
                AABB aabb = entityType.getDimensions().makeBoundingBox(vec3);

                for (VoxelShape voxelshape : level.getBlockCollisions(null, aabb)) {
                    if (!voxelshape.isEmpty()) {
                        return null;
                    }
                }

                if (entityType != EntityType.PLAYER
                    || !level.getBlockState(pos).is(BlockTags.INVALID_SPAWN_INSIDE)
                        && !level.getBlockState(pos.above()).is(BlockTags.INVALID_SPAWN_INSIDE)) {
                    return !level.getWorldBorder().isWithinBounds(aabb) ? null : vec3;
                } else {
                    return null;
                }
            }
        }
    }
}
