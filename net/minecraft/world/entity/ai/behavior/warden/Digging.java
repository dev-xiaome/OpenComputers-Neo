package net.minecraft.world.entity.ai.behavior.warden;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;

public class Digging<E extends Warden> extends Behavior<E> {
    public Digging(int duration) {
        super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), duration);
    }

    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return entity.getRemovalReason() == null;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return owner.onGround() || owner.isInWater() || owner.isInLava();
    }

    protected void start(ServerLevel level, E entity, long gameTime) {
        if (entity.onGround()) {
            entity.setPose(Pose.DIGGING);
            entity.playSound(SoundEvents.WARDEN_DIG, 5.0F, 1.0F);
        } else {
            entity.playSound(SoundEvents.WARDEN_AGITATED, 5.0F, 1.0F);
            this.stop(level, entity, gameTime);
        }
    }

    protected void stop(ServerLevel level, E entity, long gameTime) {
        if (entity.getRemovalReason() == null) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }
}
