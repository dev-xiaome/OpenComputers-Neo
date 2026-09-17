package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class RandomStroll {
    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;
    private static final int[][] SWIM_XY_DISTANCE_TIERS = new int[][]{{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};

    public static OneShot<PathfinderMob> stroll(float speedModifier) {
        return stroll(speedModifier, true);
    }

    public static OneShot<PathfinderMob> stroll(float speedModifier, boolean mayStrollFromWater) {
        return strollFlyOrSwim(
            speedModifier, p_258601_ -> LandRandomPos.getPos(p_258601_, 10, 7), mayStrollFromWater ? p_258615_ -> true : p_350044_ -> !p_350044_.isInWaterOrBubble()
        );
    }

    public static BehaviorControl<PathfinderMob> stroll(float speedModifier, int maxHorizontalDistance, int maxVerticalDistance) {
        return strollFlyOrSwim(speedModifier, p_258605_ -> LandRandomPos.getPos(p_258605_, maxHorizontalDistance, maxVerticalDistance), p_258616_ -> true);
    }

    public static BehaviorControl<PathfinderMob> fly(float speedModifier) {
        return strollFlyOrSwim(speedModifier, p_258614_ -> getTargetFlyPos(p_258614_, 10, 7), p_258602_ -> true);
    }

    public static BehaviorControl<PathfinderMob> swim(float speedModifier) {
        return strollFlyOrSwim(speedModifier, RandomStroll::getTargetSwimPos, Entity::isInWaterOrBubble);
    }

    private static OneShot<PathfinderMob> strollFlyOrSwim(float speedModifier, Function<PathfinderMob, Vec3> target, Predicate<PathfinderMob> canStroll) {
        return BehaviorBuilder.create(
            p_258620_ -> p_258620_.group(p_258620_.absent(MemoryModuleType.WALK_TARGET)).apply(p_258620_, p_258600_ -> (p_258610_, p_258611_, p_258612_) -> {
                        if (!canStroll.test(p_258611_)) {
                            return false;
                        } else {
                            Optional<Vec3> optional = Optional.ofNullable(target.apply(p_258611_));
                            p_258600_.setOrErase(optional.map(p_258622_ -> new WalkTarget(p_258622_, speedModifier, 0)));
                            return true;
                        }
                    })
        );
    }

    @Nullable
    private static Vec3 getTargetSwimPos(PathfinderMob mob) {
        Vec3 vec3 = null;
        Vec3 vec31 = null;

        for (int[] aint : SWIM_XY_DISTANCE_TIERS) {
            if (vec3 == null) {
                vec31 = BehaviorUtils.getRandomSwimmablePos(mob, aint[0], aint[1]);
            } else {
                vec31 = mob.position().add(mob.position().vectorTo(vec3).normalize().multiply((double)aint[0], (double)aint[1], (double)aint[0]));
            }

            if (vec31 == null || mob.level().getFluidState(BlockPos.containing(vec31)).isEmpty()) {
                return vec3;
            }

            vec3 = vec31;
        }

        return vec31;
    }

    @Nullable
    private static Vec3 getTargetFlyPos(PathfinderMob mob, int maxDistance, int yRange) {
        Vec3 vec3 = mob.getViewVector(0.0F);
        return AirAndWaterRandomPos.getPos(mob, maxDistance, yRange, -2, vec3.x, vec3.z, (float) (Math.PI / 2));
    }
}
