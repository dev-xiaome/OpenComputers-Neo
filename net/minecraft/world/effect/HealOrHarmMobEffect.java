package net.minecraft.world.effect;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

class HealOrHarmMobEffect extends InstantenousMobEffect {
    private final boolean isHarm;

    public HealOrHarmMobEffect(MobEffectCategory category, int color, boolean isHarm) {
        super(category, color);
        this.isHarm = isHarm;
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (this.isHarm == livingEntity.isInvertedHealAndHarm()) {
            livingEntity.heal((float)Math.max(4 << amplifier, 0));
        } else {
            livingEntity.hurt(livingEntity.damageSources().magic(), (float)(6 << amplifier));
        }

        return true;
    }

    @Override
    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity indirectSource, LivingEntity livingEntity, int amplifier, double health) {
        if (this.isHarm == livingEntity.isInvertedHealAndHarm()) {
            int i = (int)(health * (double)(4 << amplifier) + 0.5);
            livingEntity.heal((float)i);
        } else {
            int j = (int)(health * (double)(6 << amplifier) + 0.5);
            if (source == null) {
                livingEntity.hurt(livingEntity.damageSources().magic(), (float)j);
            } else {
                livingEntity.hurt(livingEntity.damageSources().indirectMagic(source, indirectSource), (float)j);
            }
        }
    }
}
