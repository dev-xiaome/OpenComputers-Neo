package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class DismountOrSkipMounting {
    public static <E extends LivingEntity> BehaviorControl<E> create(int maxDistanceFromVehicle, BiPredicate<E, Entity> shouldStopRiding) {
        return BehaviorBuilder.create(
            p_259780_ -> p_259780_.group(p_259780_.registered(MemoryModuleType.RIDE_TARGET))
                    .apply(p_259780_, p_259326_ -> (p_259287_, p_259246_, p_259462_) -> {
                            Entity entity = p_259246_.getVehicle();
                            Entity entity1 = p_259780_.<Entity>tryGet(p_259326_).orElse(null);
                            if (entity == null && entity1 == null) {
                                return false;
                            } else {
                                Entity entity2 = entity == null ? entity1 : entity;
                                if (isVehicleValid(p_259246_, entity2, maxDistanceFromVehicle) && !shouldStopRiding.test(p_259246_, entity2)) {
                                    return false;
                                } else {
                                    p_259246_.stopRiding();
                                    p_259326_.erase();
                                    return true;
                                }
                            }
                        })
        );
    }

    private static boolean isVehicleValid(LivingEntity entity, Entity vehicle, int distance) {
        return vehicle.isAlive() && vehicle.closerThan(entity, (double)distance) && vehicle.level() == entity.level();
    }
}
