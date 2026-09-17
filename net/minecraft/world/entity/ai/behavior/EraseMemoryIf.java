package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class EraseMemoryIf {
    public static <E extends LivingEntity> BehaviorControl<E> create(Predicate<E> shouldEraseMemory, MemoryModuleType<?> erasingMemory) {
        return BehaviorBuilder.create(
            p_260008_ -> p_260008_.group(p_260008_.present(erasingMemory)).apply(p_260008_, p_259127_ -> (p_259033_, p_259929_, p_260086_) -> {
                        if (shouldEraseMemory.test(p_259929_)) {
                            p_259127_.erase();
                            return true;
                        } else {
                            return false;
                        }
                    })
        );
    }
}
