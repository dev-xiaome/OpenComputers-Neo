package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class DefaultRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int radius, int verticalDistance) {
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = RandomPos.generateRandomDirection(mob.getRandom(), radius, verticalDistance);
            return generateRandomPosTowardDirection(mob, radius, flag, blockpos);
        });
    }

    @Nullable
    public static Vec3 getPosTowards(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition, double amplifier) {
        Vec3 vec3 = vectorPosition.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), radius, yRange, 0, vec3.x, vec3.z, amplifier);
            return blockpos == null ? null : generateRandomPosTowardDirection(mob, radius, flag, blockpos);
        });
    }

    @Nullable
    public static Vec3 getPosAway(PathfinderMob mob, int radius, int yRange, Vec3 vectorPosition) {
        Vec3 vec3 = mob.position().subtract(vectorPosition);
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(
            mob,
            () -> {
                BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(
                    mob.getRandom(), radius, yRange, 0, vec3.x, vec3.z, (float) (Math.PI / 2)
                );
                return blockpos == null ? null : generateRandomPosTowardDirection(mob, radius, flag, blockpos);
            }
        );
    }

    @Nullable
    private static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int radius, boolean shortCircuit, BlockPos pos) {
        BlockPos blockpos = RandomPos.generateRandomPosTowardDirection(mob, radius, mob.getRandom(), pos);
        return !GoalUtils.isOutsideLimits(blockpos, mob)
                && !GoalUtils.isRestricted(shortCircuit, mob, blockpos)
                && !GoalUtils.isNotStable(mob.getNavigation(), blockpos)
                && !GoalUtils.hasMalus(mob, blockpos)
            ? blockpos
            : null;
    }
}
