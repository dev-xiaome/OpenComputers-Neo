package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

@Deprecated
public class SetEntityLookTargetSometimes {
    public static BehaviorControl<LivingEntity> create(float maxDist, UniformInt interval) {
        return create(maxDist, interval, p_259715_ -> true);
    }

    public static BehaviorControl<LivingEntity> create(EntityType<?> entityType, float maxDist, UniformInt interval) {
        return create(maxDist, interval, p_348234_ -> entityType.equals(p_348234_.getType()));
    }

    private static BehaviorControl<LivingEntity> create(float maxDist, UniformInt interval, Predicate<LivingEntity> canLookAtTarget) {
        float f = maxDist * maxDist;
        SetEntityLookTargetSometimes.Ticker setentitylooktargetsometimes$ticker = new SetEntityLookTargetSometimes.Ticker(interval);
        return BehaviorBuilder.create(
            p_259288_ -> p_259288_.group(p_259288_.absent(MemoryModuleType.LOOK_TARGET), p_259288_.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES))
                    .apply(
                        p_259288_,
                        (p_259350_, p_260134_) -> (p_264952_, p_264953_, p_264954_) -> {
                                Optional<LivingEntity> optional = p_259288_.<NearestVisibleLivingEntities>get(p_260134_)
                                    .findClosest(canLookAtTarget.and(p_325740_ -> p_325740_.distanceToSqr(p_264953_) <= (double)f));
                                if (optional.isEmpty()) {
                                    return false;
                                } else if (!setentitylooktargetsometimes$ticker.tickDownAndCheck(p_264952_.random)) {
                                    return false;
                                } else {
                                    p_259350_.set(new EntityTracker(optional.get(), true));
                                    return true;
                                }
                            }
                    )
        );
    }

    public static final class Ticker {
        private final UniformInt interval;
        private int ticksUntilNextStart;

        public Ticker(UniformInt interval) {
            if (interval.getMinValue() <= 1) {
                throw new IllegalArgumentException();
            } else {
                this.interval = interval;
            }
        }

        public boolean tickDownAndCheck(RandomSource random) {
            if (this.ticksUntilNextStart == 0) {
                this.ticksUntilNextStart = this.interval.sample(random) - 1;
                return false;
            } else {
                return --this.ticksUntilNextStart == 0;
            }
        }
    }
}
