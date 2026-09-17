package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public class RamTarget extends Behavior<Goat> {
    public static final int TIME_OUT_DURATION = 200;
    public static final float RAM_SPEED_FORCE_FACTOR = 1.65F;
    private final Function<Goat, UniformInt> getTimeBetweenRams;
    private final TargetingConditions ramTargeting;
    private final float speed;
    private final ToDoubleFunction<Goat> getKnockbackForce;
    private Vec3 ramDirection;
    private final Function<Goat, SoundEvent> getImpactSound;
    private final Function<Goat, SoundEvent> getHornBreakSound;

    public RamTarget(
        Function<Goat, UniformInt> getTimeBetweenRams,
        TargetingConditions ramTargeting,
        float speed,
        ToDoubleFunction<Goat> getKnockbackForce,
        Function<Goat, SoundEvent> getImpactSound,
        Function<Goat, SoundEvent> getHornBreakSound
    ) {
        super(ImmutableMap.of(MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_PRESENT), 200);
        this.getTimeBetweenRams = getTimeBetweenRams;
        this.ramTargeting = ramTargeting;
        this.speed = speed;
        this.getKnockbackForce = getKnockbackForce;
        this.getImpactSound = getImpactSound;
        this.getHornBreakSound = getHornBreakSound;
        this.ramDirection = Vec3.ZERO;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Goat owner) {
        return owner.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET);
    }

    protected boolean canStillUse(ServerLevel level, Goat entity, long gameTime) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET);
    }

    protected void start(ServerLevel level, Goat entity, long gameTime) {
        BlockPos blockpos = entity.blockPosition();
        Brain<?> brain = entity.getBrain();
        Vec3 vec3 = brain.getMemory(MemoryModuleType.RAM_TARGET).get();
        this.ramDirection = new Vec3((double)blockpos.getX() - vec3.x(), 0.0, (double)blockpos.getZ() - vec3.z()).normalize();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(vec3, this.speed, 0));
    }

    protected void tick(ServerLevel level, Goat owner, long gameTime) {
        List<LivingEntity> list = level.getNearbyEntities(LivingEntity.class, this.ramTargeting, owner, owner.getBoundingBox());
        Brain<?> brain = owner.getBrain();
        if (!list.isEmpty()) {
            LivingEntity livingentity = list.get(0);
            DamageSource damagesource = level.damageSources().noAggroMobAttack(owner);
            if (livingentity.hurt(damagesource, (float)owner.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                EnchantmentHelper.doPostAttackEffects(level, livingentity, damagesource);
            }

            int i = owner.hasEffect(MobEffects.MOVEMENT_SPEED) ? owner.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() + 1 : 0;
            int j = owner.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? owner.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() + 1 : 0;
            float f = 0.25F * (float)(i - j);
            float f1 = Mth.clamp(owner.getSpeed() * 1.65F, 0.2F, 3.0F) + f;
            float f2 = livingentity.isDamageSourceBlocked(level.damageSources().mobAttack(owner)) ? 0.5F : 1.0F;
            livingentity.knockback((double)(f2 * f1) * this.getKnockbackForce.applyAsDouble(owner), this.ramDirection.x(), this.ramDirection.z());
            this.finishRam(level, owner);
            level.playSound(null, owner, this.getImpactSound.apply(owner), SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (this.hasRammedHornBreakingBlock(level, owner)) {
            level.playSound(null, owner, this.getImpactSound.apply(owner), SoundSource.NEUTRAL, 1.0F, 1.0F);
            boolean flag = owner.dropHorn();
            if (flag) {
                level.playSound(null, owner, this.getHornBreakSound.apply(owner), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }

            this.finishRam(level, owner);
        } else {
            Optional<WalkTarget> optional = brain.getMemory(MemoryModuleType.WALK_TARGET);
            Optional<Vec3> optional1 = brain.getMemory(MemoryModuleType.RAM_TARGET);
            boolean flag1 = optional.isEmpty() || optional1.isEmpty() || optional.get().getTarget().currentPosition().closerThan(optional1.get(), 0.25);
            if (flag1) {
                this.finishRam(level, owner);
            }
        }
    }

    private boolean hasRammedHornBreakingBlock(ServerLevel level, Goat owner) {
        Vec3 vec3 = owner.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize();
        BlockPos blockpos = BlockPos.containing(owner.position().add(vec3));
        return level.getBlockState(blockpos).is(BlockTags.SNAPS_GOAT_HORN) || level.getBlockState(blockpos.above()).is(BlockTags.SNAPS_GOAT_HORN);
    }

    protected void finishRam(ServerLevel level, Goat owner) {
        level.broadcastEntityEvent(owner, (byte)59);
        owner.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, this.getTimeBetweenRams.apply(owner).sample(level.random));
        owner.getBrain().eraseMemory(MemoryModuleType.RAM_TARGET);
    }
}
