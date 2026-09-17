package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class StayCloseToTarget {
    public static BehaviorControl<LivingEntity> create(
        Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter, Predicate<LivingEntity> predicate, int closeEnoughDist, int tooClose, float speedModifier
    ) {
        return BehaviorBuilder.create(
            p_272460_ -> p_272460_.group(p_272460_.registered(MemoryModuleType.LOOK_TARGET), p_272460_.registered(MemoryModuleType.WALK_TARGET))
                    .apply(p_272460_, (p_272466_, p_272467_) -> (p_260054_, p_260069_, p_259517_) -> {
                            Optional<PositionTracker> optional = targetPositionGetter.apply(p_260069_);
                            if (!optional.isEmpty() && predicate.test(p_260069_)) {
                                PositionTracker positiontracker = optional.get();
                                if (p_260069_.position().closerThan(positiontracker.currentPosition(), (double)tooClose)) {
                                    return false;
                                } else {
                                    PositionTracker positiontracker1 = optional.get();
                                    p_272466_.set(positiontracker1);
                                    p_272467_.set(new WalkTarget(positiontracker1, speedModifier, closeEnoughDist));
                                    return true;
                                }
                            } else {
                                return false;
                            }
                        })
        );
    }
}
