package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class AirAndWaterRandomPos {
    @Nullable
    public static Vec3 getPos(PathfinderMob mob, int maxDistance, int yRange, int y, double x, double z, double amplifier) {
        boolean flag = GoalUtils.mobRestricted(mob, maxDistance);
        return RandomPos.generateRandomPos(
            mob, () -> generateRandomPos(mob, maxDistance, yRange, y, x, z, amplifier, flag)
        );
    }

    @Nullable
    public static BlockPos generateRandomPos(
        PathfinderMob mob, int maxDistance, int yRange, int y, double x, double z, double amplifier, boolean shortCircuit
    ) {
        BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(
            mob.getRandom(), maxDistance, yRange, y, x, z, amplifier
        );
        if (blockpos == null) {
            return null;
        } else {
            BlockPos blockpos1 = RandomPos.generateRandomPosTowardDirection(mob, maxDistance, mob.getRandom(), blockpos);
            if (!GoalUtils.isOutsideLimits(blockpos1, mob) && !GoalUtils.isRestricted(shortCircuit, mob, blockpos1)) {
                blockpos1 = RandomPos.moveUpOutOfSolid(blockpos1, mob.level().getMaxBuildHeight(), p_148376_ -> GoalUtils.isSolid(mob, p_148376_));
                return GoalUtils.hasMalus(mob, blockpos1) ? null : blockpos1;
            } else {
                return null;
            }
        }
    }
}
