package net.minecraft.world.entity.ai.sensing;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class AxolotlAttackablesSensor extends NearestVisibleLivingEntitySensor {
    public static final float TARGET_DETECTION_DISTANCE = 8.0F;

    /**
     * @return if the second entity is hostile to the axolotl or is huntable by it
     */
    @Override
    protected boolean isMatchingEntity(LivingEntity attacker, LivingEntity target) {
        return this.isClose(attacker, target)
            && target.isInWaterOrBubble()
            && (this.isHostileTarget(target) || this.isHuntTarget(attacker, target))
            && Sensor.isEntityAttackable(attacker, target);
    }

    private boolean isHuntTarget(LivingEntity attacker, LivingEntity target) {
        return !attacker.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN) && target.getType().is(EntityTypeTags.AXOLOTL_HUNT_TARGETS);
    }

    private boolean isHostileTarget(LivingEntity target) {
        return target.getType().is(EntityTypeTags.AXOLOTL_ALWAYS_HOSTILES);
    }

    private boolean isClose(LivingEntity attacker, LivingEntity target) {
        return target.distanceToSqr(attacker) <= 64.0;
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleType.NEAREST_ATTACKABLE;
    }
}
