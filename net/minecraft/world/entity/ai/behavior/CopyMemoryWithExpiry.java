package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class CopyMemoryWithExpiry {
    public static <E extends LivingEntity, T> BehaviorControl<E> create(
        Predicate<E> canCopyMemory, MemoryModuleType<? extends T> sourceMemory, MemoryModuleType<T> targetMemory, UniformInt durationOfCopy
    ) {
        return BehaviorBuilder.create(
            p_260141_ -> p_260141_.group(p_260141_.present(sourceMemory), p_260141_.absent(targetMemory))
                    .apply(p_260141_, (p_259306_, p_259907_) -> (p_264887_, p_264888_, p_264889_) -> {
                            if (!canCopyMemory.test(p_264888_)) {
                                return false;
                            } else {
                                p_259907_.setWithExpiry(p_260141_.get(p_259306_), (long)durationOfCopy.sample(p_264887_.random));
                                return true;
                            }
                        })
        );
    }
}
