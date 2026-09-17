package net.minecraft.world.entity.ai.util;

import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class LandRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int verticalRange) {
        return getPos(mob, radius, verticalRange, mob::getWalkTargetValue);
    }

    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int yRange, ToDoubleFunction<BlockPos> toDoubleFunction) {
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(() -> {
            BlockPos blockpos = RandomPos.generateRandomDirection(mob.getRandom(), radius, yRange);
            BlockPos blockpos1 = generateRandomPosTowardDirection(mob, radius, flag, blockpos);
            return blockpos1 == null ? null : movePosUpOutOfSolid(mob, blockpos1);
        }, toDoubleFunction);
    }

    @Nullable
    public static Vec3 getPosTowards(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = vectorPosition.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return getPosInDirection(mob, radius, yRange, vec3, flag);
    }

    @Nullable
    public static Vec3 getPosAway(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = mob.position().subtract(vectorPosition);
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return getPosInDirection(mob, radius, yRange, vec3, flag);
    }

    @Nullable
    private static Vec3 getPosInDirection(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition, boolean shortCircuit) {
        return RandomPos.generateRandomPos(
            mob,
            () -> {
                BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(
                    mob.getRandom(), radius, yRange, 0, vectorPosition.x, vectorPosition.z, (float) (Math.PI / 2)
                );
                if (blockpos == null) {
                    return null;
                } else {
                    BlockPos blockpos1 = generateRandomPosTowardDirection(mob, radius, shortCircuit, blockpos);
                    return blockpos1 == null ? null : movePosUpOutOfSolid(mob, blockpos1);
                }
            }
        );
    }

    @Nullable
    public static BlockPos movePosUpOutOfSolid(PathfinderMob mob, BlockPos pos) {
        pos = RandomPos.moveUpOutOfSolid(pos, mob.level().getMaxBuildHeight(), p_148534_ -> GoalUtils.isSolid(mob, p_148534_));
        return !GoalUtils.isWater(mob, pos) && !GoalUtils.hasMalus(mob, pos) ? pos : null;
    }

    @Nullable
    public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int radius, boolean shortCircuit, BlockPos pos) {
        BlockPos blockpos = RandomPos.generateRandomPosTowardDirection(mob, radius, mob.getRandom(), pos);
        return !GoalUtils.isOutsideLimits(blockpos, mob)
                && !GoalUtils.isRestricted(shortCircuit, mob, blockpos)
                && !GoalUtils.isNotStable(mob.getNavigation(), blockpos)
            ? blockpos
            : null;
    }
}
