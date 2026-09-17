package net.minecraft.world.entity.ai.goal.target;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raider;

public class NearestAttackableWitchTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
    private boolean canAttack = true;

    public NearestAttackableWitchTargetGoal(
        Raider mob, Class<T> targetType, int randomInterval, boolean mustSee, boolean mustReach, @Nullable Predicate<LivingEntity> targetPredicate
    ) {
        super(mob, targetType, randomInterval, mustSee, mustReach, targetPredicate);
    }

    public void setCanAttack(boolean active) {
        this.canAttack = active;
    }

    @Override
    public boolean canUse() {
        return this.canAttack && super.canUse();
    }
}
