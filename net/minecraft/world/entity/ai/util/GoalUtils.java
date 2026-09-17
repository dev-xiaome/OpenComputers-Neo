package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class GoalUtils {
    public static boolean hasGroundPathNavigation(Mob mob) {
        return mob.getNavigation() instanceof GroundPathNavigation;
    }

    /**
     * @return if a mob is stuck, within a certain radius beyond it's restriction radius
     */
    public static boolean mobRestricted(PathfinderMob mob, int radius) {
        return mob.hasRestriction()
            && mob.getRestrictCenter().closerToCenterThan(mob.position(), (double)(mob.getRestrictRadius() + (float)radius) + 1.0);
    }

    /**
     * @return if a mob is above or below the map
     */
    public static boolean isOutsideLimits(BlockPos pos, PathfinderMob mob) {
        return pos.getY() < mob.level().getMinBuildHeight() || pos.getY() > mob.level().getMaxBuildHeight();
    }

    /**
     * @return if a mob is restricted. The first parameter short circuits the operation.
     */
    public static boolean isRestricted(boolean shortCircuit, PathfinderMob mob, BlockPos pos) {
        return shortCircuit && !mob.isWithinRestriction(pos);
    }

    /**
     * @return if the destination can't be pathfinded to
     */
    public static boolean isNotStable(PathNavigation navigation, BlockPos pos) {
        return !navigation.isStableDestination(pos);
    }

    /**
     * @return if the position is water in the mob's level
     */
    public static boolean isWater(PathfinderMob mob, BlockPos pos) {
        return mob.level().getFluidState(pos).is(FluidTags.WATER);
    }

    /**
     * @return if the pathfinding malus exists
     */
    public static boolean hasMalus(PathfinderMob mob, BlockPos pos) {
        return mob.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(mob, pos)) != 0.0F;
    }

    /**
     * @return if the mob is standing on a solid material
     */
    public static boolean isSolid(PathfinderMob mob, BlockPos pos) {
        return mob.level().getBlockState(pos).isSolid();
    }
}
