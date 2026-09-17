package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class SetLookAndInteract {
    public static BehaviorControl<LivingEntity> create(EntityType<?> entityType, int maxDist) {
        int i = maxDist * maxDist;
        return BehaviorBuilder.create(
            p_258685_ -> p_258685_.group(
                        p_258685_.registered(MemoryModuleType.LOOK_TARGET),
                        p_258685_.absent(MemoryModuleType.INTERACTION_TARGET),
                        p_258685_.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    )
                    .apply(
                        p_258685_,
                        (p_258680_, p_258681_, p_258682_) -> (p_258670_, p_258671_, p_258672_) -> {
                                Optional<LivingEntity> optional = p_258685_.<NearestVisibleLivingEntities>get(p_258682_)
                                    .findClosest(p_348238_ -> p_348238_.distanceToSqr(p_258671_) <= (double)i && entityType.equals(p_348238_.getType()));
                                if (optional.isEmpty()) {
                                    return false;
                                } else {
                                    LivingEntity livingentity = optional.get();
                                    p_258681_.set(livingentity);
                                    p_258680_.set(new EntityTracker(livingentity, true));
                                    return true;
                                }
                            }
                    )
        );
    }
}
