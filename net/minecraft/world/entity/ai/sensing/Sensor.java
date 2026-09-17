package net.minecraft.world.entity.ai.sensing;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

public abstract class Sensor<E extends LivingEntity> {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int DEFAULT_SCAN_RATE = 20;
    protected static final int TARGETING_RANGE = 16;
    private static final TargetingConditions TARGET_CONDITIONS = TargetingConditions.forNonCombat().range(16.0);
    private static final TargetingConditions TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forNonCombat()
        .range(16.0)
        .ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS = TargetingConditions.forCombat().range(16.0);
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forCombat()
        .range(16.0)
        .ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT = TargetingConditions.forCombat().range(16.0).ignoreLineOfSight();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT = TargetingConditions.forCombat()
        .range(16.0)
        .ignoreLineOfSight()
        .ignoreInvisibilityTesting();
    private final int scanRate;
    private long timeToTick;

    public Sensor(int scanRate) {
        this.scanRate = scanRate;
        this.timeToTick = (long)RANDOM.nextInt(scanRate);
    }

    public Sensor() {
        this(20);
    }

    public final void tick(ServerLevel level, E entity) {
        if (--this.timeToTick <= 0L) {
            this.timeToTick = (long)this.scanRate;
            this.doTick(level, entity);
        }
    }

    protected abstract void doTick(ServerLevel level, E entity);

    public abstract Set<MemoryModuleType<?>> requires();

    /**
     * @return if the entity is remembered as a target and then tests the condition
     */
    public static boolean isEntityTargetable(LivingEntity livingEntity, LivingEntity target) {
        return livingEntity.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
            ? TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(livingEntity, target)
            : TARGET_CONDITIONS.test(livingEntity, target);
    }

    /**
     * @return if entity is remembered as an attack target and is valid to attack
     */
    public static boolean isEntityAttackable(LivingEntity attacker, LivingEntity target) {
        return attacker.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
            ? ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(attacker, target)
            : ATTACK_TARGET_CONDITIONS.test(attacker, target);
    }

    public static boolean isEntityAttackableIgnoringLineOfSight(LivingEntity attacker, LivingEntity target) {
        return attacker.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
            ? ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT.test(attacker, target)
            : ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT.test(attacker, target);
    }
}
