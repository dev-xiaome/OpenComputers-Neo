package net.minecraft.world.effect;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

class OozingMobEffect extends MobEffect {
    private static final int RADIUS_TO_CHECK_SLIMES = 2;
    public static final int SLIME_SIZE = 2;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected OozingMobEffect(MobEffectCategory category, int color, ToIntFunction<RandomSource> spawnedCount) {
        super(category, color, ParticleTypes.ITEM_SLIME);
        this.spawnedCount = spawnedCount;
    }

    @VisibleForTesting
    protected static int numberOfSlimesToSpawn(int maxEntityCramming, OozingMobEffect.NearbySlimes nearbySlimes, int spawnCount) {
        return maxEntityCramming < 1 ? spawnCount : Mth.clamp(0, maxEntityCramming - nearbySlimes.count(maxEntityCramming), spawnCount);
    }

    @Override
    public void onMobRemoved(LivingEntity livingEntity, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED) {
            int i = this.spawnedCount.applyAsInt(livingEntity.getRandom());
            Level level = livingEntity.level();
            int j = level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
            int k = numberOfSlimesToSpawn(j, OozingMobEffect.NearbySlimes.closeTo(livingEntity), i);

            for (int l = 0; l < k; l++) {
                this.spawnSlimeOffspring(livingEntity.level(), livingEntity.getX(), livingEntity.getY() + 0.5, livingEntity.getZ());
            }
        }
    }

    private void spawnSlimeOffspring(Level level, double x, double y, double z) {
        Slime slime = EntityType.SLIME.create(level);
        if (slime != null) {
            slime.setSize(2, true);
            slime.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(slime);
        }
    }

    @FunctionalInterface
    protected interface NearbySlimes {
        int count(int maxEntityCramming);

        static OozingMobEffect.NearbySlimes closeTo(LivingEntity entity) {
            return p_352702_ -> {
                List<Slime> list = new ArrayList<>();
                entity.level().getEntities(EntityType.SLIME, entity.getBoundingBox().inflate(2.0), p_348647_ -> p_348647_ != entity, list, p_352702_);
                return list.size();
            };
        }
    }
}
