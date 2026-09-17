package net.minecraft.world.effect;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class MobEffectUtil {
    public static Component formatDuration(MobEffectInstance effect, float durationFactor, float ticksPerSecond) {
        if (effect.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        } else {
            int i = Mth.floor((float)effect.getDuration() * durationFactor);
            return Component.literal(StringUtil.formatTickDuration(i, ticksPerSecond));
        }
    }

    public static boolean hasDigSpeed(LivingEntity entity) {
        return entity.hasEffect(MobEffects.DIG_SPEED) || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static int getDigSpeedAmplification(LivingEntity entity) {
        int i = 0;
        int j = 0;
        if (entity.hasEffect(MobEffects.DIG_SPEED)) {
            i = entity.getEffect(MobEffects.DIG_SPEED).getAmplifier();
        }

        if (entity.hasEffect(MobEffects.CONDUIT_POWER)) {
            j = entity.getEffect(MobEffects.CONDUIT_POWER).getAmplifier();
        }

        return Math.max(i, j);
    }

    public static boolean hasWaterBreathing(LivingEntity entity) {
        return entity.hasEffect(MobEffects.WATER_BREATHING) || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(
        ServerLevel level, @Nullable Entity source, Vec3 pos, double radius, MobEffectInstance effect, int duration
    ) {
        Holder<MobEffect> holder = effect.getEffect();
        List<ServerPlayer> list = level.getPlayers(
            p_267925_ -> p_267925_.gameMode.isSurvival()
                    && (source == null || !source.isAlliedTo(p_267925_))
                    && pos.closerThan(p_267925_.position(), radius)
                    && (
                        !p_267925_.hasEffect(holder)
                            || p_267925_.getEffect(holder).getAmplifier() < effect.getAmplifier()
                            || p_267925_.getEffect(holder).endsWithin(duration - 1)
                    )
        );
        list.forEach(p_238232_ -> p_238232_.addEffect(new MobEffectInstance(effect), source));
        return list;
    }
}
