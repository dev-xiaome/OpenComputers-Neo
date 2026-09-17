package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * A LootItemCondition that checks whether it currently raining or thundering.
 * Both checks are optional.
 */
public record WeatherCheck(Optional<Boolean> isRaining, Optional<Boolean> isThundering) implements LootItemCondition {
    public static final MapCodec<WeatherCheck> CODEC = RecordCodecBuilder.mapCodec(
        p_338174_ -> p_338174_.group(
                    Codec.BOOL.optionalFieldOf("raining").forGetter(WeatherCheck::isRaining),
                    Codec.BOOL.optionalFieldOf("thundering").forGetter(WeatherCheck::isThundering)
                )
                .apply(p_338174_, WeatherCheck::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.WEATHER_CHECK;
    }

    public boolean test(LootContext context) {
        ServerLevel serverlevel = context.getLevel();
        return this.isRaining.isPresent() && this.isRaining.get() != serverlevel.isRaining()
            ? false
            : !this.isThundering.isPresent() || this.isThundering.get() == serverlevel.isThundering();
    }

    public static WeatherCheck.Builder weather() {
        return new WeatherCheck.Builder();
    }

    public static class Builder implements LootItemCondition.Builder {
        private Optional<Boolean> isRaining = Optional.empty();
        private Optional<Boolean> isThundering = Optional.empty();

        public WeatherCheck.Builder setRaining(boolean isRaining) {
            this.isRaining = Optional.of(isRaining);
            return this;
        }

        public WeatherCheck.Builder setThundering(boolean isThundering) {
            this.isThundering = Optional.of(isThundering);
            return this;
        }

        public WeatherCheck build() {
            return new WeatherCheck(this.isRaining, this.isThundering);
        }
    }
}
