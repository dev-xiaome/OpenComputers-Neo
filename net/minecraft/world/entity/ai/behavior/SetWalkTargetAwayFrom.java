package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class SetWalkTargetAwayFrom {
    public static BehaviorControl<PathfinderMob> pos(MemoryModuleType<BlockPos> walkTargetAwayFromMemory, float speedModifier, int desiredDistance, boolean hasTarget) {
        return create(walkTargetAwayFromMemory, speedModifier, desiredDistance, hasTarget, Vec3::atBottomCenterOf);
    }

    public static OneShot<PathfinderMob> entity(MemoryModuleType<? extends Entity> walkTargetAwayFromMemory, float speedModifier, int desiredDistance, boolean hasTarget) {
        return create(walkTargetAwayFromMemory, speedModifier, desiredDistance, hasTarget, Entity::position);
    }

    private static <T> OneShot<PathfinderMob> create(
        MemoryModuleType<T> walkTargetAwayFromMemory, float speedModifier, int desiredDistance, boolean hasTarget, Function<T, Vec3> toPosition
    ) {
        return BehaviorBuilder.create(
            p_259292_ -> p_259292_.group(p_259292_.registered(MemoryModuleType.WALK_TARGET), p_259292_.present(walkTargetAwayFromMemory))
                    .apply(p_259292_, (p_260063_, p_260053_) -> (p_259973_, p_259323_, p_259275_) -> {
                            Optional<WalkTarget> optional = p_259292_.tryGet(p_260063_);
                            if (optional.isPresent() && !hasTarget) {
                                return false;
                            } else {
                                Vec3 vec3 = p_259323_.position();
                                Vec3 vec31 = toPosition.apply(p_259292_.get(p_260053_));
                                if (!vec3.closerThan(vec31, (double)desiredDistance)) {
                                    return false;
                                } else {
                                    if (optional.isPresent() && optional.get().getSpeedModifier() == speedModifier) {
                                        Vec3 vec32 = optional.get().getTarget().currentPosition().subtract(vec3);
                                        Vec3 vec33 = vec31.subtract(vec3);
                                        if (vec32.dot(vec33) < 0.0) {
                                            return false;
                                        }
                                    }

                                    for (int i = 0; i < 10; i++) {
                                        Vec3 vec34 = LandRandomPos.getPosAway(p_259323_, 16, 7, vec31);
                                        if (vec34 != null) {
                                            p_260063_.set(new WalkTarget(vec34, speedModifier, 0));
                                            break;
                                        }
                                    }

                                    return true;
                                }
                            }
                        })
        );
    }
}
