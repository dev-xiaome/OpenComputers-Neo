package net.minecraft.world.entity.ai.util;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class HoverRandomPos {
    /**
     * Tries to generate a random position a couple different ways, and if failing, sees if swimming vertically is an option.
     */
    @Nullable
    public static Vec3 getPos(
        PathfinderMob mob, int radius, int yRange, double x, double z, float amplifier, int maxSwimUp, int minSwimUp
    ) {
        boolean flag = GoalUtils.mobRestricted(mob, radius);
        return RandomPos.generateRandomPos(
            mob,
            () -> {
                BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(
                    mob.getRandom(), radius, yRange, 0, x, z, (double)amplifier
                );
                if (blockpos == null) {
                    return null;
                } else {
                    BlockPos blockpos1 = LandRandomPos.generateRandomPosTowardDirection(mob, radius, flag, blockpos);
                    if (blockpos1 == null) {
                        return null;
                    } else {
                        blockpos1 = RandomPos.moveUpToAboveSolid(
                            blockpos1,
                            mob.getRandom().nextInt(maxSwimUp - minSwimUp + 1) + minSwimUp,
                            mob.level().getMaxBuildHeight(),
                            p_148486_ -> GoalUtils.isSolid(mob, p_148486_)
                        );
                        return !GoalUtils.isWater(mob, blockpos1) && !GoalUtils.hasMalus(mob, blockpos1) ? blockpos1 : null;
                    }
                }
            }
        );
    }
}
