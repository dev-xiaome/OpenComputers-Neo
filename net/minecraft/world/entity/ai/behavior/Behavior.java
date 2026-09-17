package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public abstract class Behavior<E extends LivingEntity> implements BehaviorControl<E> {
    public static final int DEFAULT_DURATION = 60;
    protected final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private Behavior.Status status = Behavior.Status.STOPPED;
    private long endTimestamp;
    private final int minDuration;
    private final int maxDuration;

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        this(entryCondition, 60);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int duration) {
        this(entryCondition, duration, duration);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int minDuration, int maxDuration) {
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.entryCondition = entryCondition;
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    @Override
    public final boolean tryStart(ServerLevel level, E owner, long gameTime) {
        if (this.hasRequiredMemories(owner) && this.checkExtraStartConditions(level, owner)) {
            this.status = Behavior.Status.RUNNING;
            int i = this.minDuration + level.getRandom().nextInt(this.maxDuration + 1 - this.minDuration);
            this.endTimestamp = gameTime + (long)i;
            this.start(level, owner, gameTime);
            return true;
        } else {
            return false;
        }
    }

    protected void start(ServerLevel level, E entity, long gameTime) {
    }

    @Override
    public final void tickOrStop(ServerLevel level, E entity, long gameTime) {
        if (!this.timedOut(gameTime) && this.canStillUse(level, entity, gameTime)) {
            this.tick(level, entity, gameTime);
        } else {
            this.doStop(level, entity, gameTime);
        }
    }

    protected void tick(ServerLevel level, E owner, long gameTime) {
    }

    @Override
    public final void doStop(ServerLevel level, E entity, long gameTime) {
        this.status = Behavior.Status.STOPPED;
        this.stop(level, entity, gameTime);
    }

    protected void stop(ServerLevel level, E entity, long gameTime) {
    }

    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return false;
    }

    protected boolean timedOut(long gameTime) {
        return gameTime > this.endTimestamp;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return true;
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    protected boolean hasRequiredMemories(E owner) {
        for (Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memorymoduletype = entry.getKey();
            MemoryStatus memorystatus = entry.getValue();
            if (!owner.getBrain().checkMemory(memorymoduletype, memorystatus)) {
                return false;
            }
        }

        return true;
    }

    public static enum Status {
        STOPPED,
        RUNNING;
    }
}
