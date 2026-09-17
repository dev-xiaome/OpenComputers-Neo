package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InteractWith {
    public static <T extends LivingEntity> BehaviorControl<LivingEntity> of(
        EntityType<? extends T> type, int interactionRange, MemoryModuleType<T> interactMemory, float speedModifier, int maxDist
    ) {
        return of(type, interactionRange, p_23287_ -> true, p_23285_ -> true, interactMemory, speedModifier, maxDist);
    }

    public static <E extends LivingEntity, T extends LivingEntity> BehaviorControl<E> of(
        EntityType<? extends T> type,
        int interactionRange,
        Predicate<E> selfFilter,
        Predicate<T> targetFilter,
        MemoryModuleType<T> memory,
        float speedModifier,
        int maxDist
    ) {
        int i = interactionRange * interactionRange;
        Predicate<LivingEntity> predicate = p_348182_ -> type.equals(p_348182_.getType()) && targetFilter.test((T)p_348182_);
        return BehaviorBuilder.create(
            p_258426_ -> p_258426_.group(
                        p_258426_.registered(memory),
                        p_258426_.registered(MemoryModuleType.LOOK_TARGET),
                        p_258426_.absent(MemoryModuleType.WALK_TARGET),
                        p_258426_.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    )
                    .apply(
                        p_258426_,
                        (p_258439_, p_258440_, p_258441_, p_258442_) -> (p_258413_, p_258414_, p_258415_) -> {
                                NearestVisibleLivingEntities nearestvisiblelivingentities = p_258426_.get(p_258442_);
                                if (selfFilter.test(p_258414_) && nearestvisiblelivingentities.contains(predicate)) {
                                    Optional<LivingEntity> optional = nearestvisiblelivingentities.findClosest(
                                        p_325690_ -> p_325690_.distanceToSqr(p_258414_) <= (double)i && predicate.test(p_325690_)
                                    );
                                    optional.ifPresent(p_258432_ -> {
                                        p_258439_.set((T)p_258432_);
                                        p_258440_.set(new EntityTracker(p_258432_, true));
                                        p_258441_.set(new WalkTarget(new EntityTracker(p_258432_, false), speedModifier, maxDist));
                                    });
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                    )
        );
    }
}
