package net.minecraft.world.entity.monster.hoglin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public interface HoglinBase {
    int ATTACK_ANIMATION_DURATION = 10;

    int getAttackAnimationRemainingTicks();

    static boolean hurtAndThrowTarget(LivingEntity hoglin, LivingEntity target) {
        float f1 = (float)hoglin.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float f;
        if (!hoglin.isBaby() && (int)f1 > 0) {
            f = f1 / 2.0F + (float)hoglin.level().random.nextInt((int)f1);
        } else {
            f = f1;
        }

        DamageSource damagesource = hoglin.damageSources().mobAttack(hoglin);
        boolean flag = target.hurt(damagesource, f);
        if (flag) {
            if (hoglin.level() instanceof ServerLevel serverlevel) {
                EnchantmentHelper.doPostAttackEffects(serverlevel, target, damagesource);
            }

            if (!hoglin.isBaby()) {
                throwTarget(hoglin, target);
            }
        }

        return flag;
    }

    static void throwTarget(LivingEntity hoglin, LivingEntity target) {
        double d0 = hoglin.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        double d1 = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double d2 = d0 - d1;
        if (!(d2 <= 0.0)) {
            double d3 = target.getX() - hoglin.getX();
            double d4 = target.getZ() - hoglin.getZ();
            float f = (float)(hoglin.level().random.nextInt(21) - 10);
            double d5 = d2 * (double)(hoglin.level().random.nextFloat() * 0.5F + 0.2F);
            Vec3 vec3 = new Vec3(d3, 0.0, d4).normalize().scale(d5).yRot(f);
            double d6 = d2 * (double)hoglin.level().random.nextFloat() * 0.5;
            target.push(vec3.x, d6, vec3.z);
            target.hurtMarked = true;
        }
    }
}
