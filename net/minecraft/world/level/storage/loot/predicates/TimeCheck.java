package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

/**
 * A LootItemCondition that checks the {@linkplain ServerLevel#getDayTime day time} against an {@link IntRange} after applying an optional modulo division.
 */
public record TimeCheck(Optional<Long> period, IntRange value) implements LootItemCondition {
    public static final MapCodec<TimeCheck> CODEC = RecordCodecBuilder.mapCodec(
        p_338173_ -> p_338173_.group(
                    Codec.LONG.optionalFieldOf("period").forGetter(TimeCheck::period), IntRange.CODEC.fieldOf("value").forGetter(TimeCheck::value)
                )
                .apply(p_338173_, TimeCheck::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.TIME_CHECK;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.value.getReferencedContextParams();
    }

    public boolean test(LootContext context) {
        ServerLevel serverlevel = context.getLevel();
        long i = serverlevel.getDayTime();
        if (this.period.isPresent()) {
            i %= this.period.get();
        }

        return this.value.test(context, (int)i);
    }

    public static TimeCheck.Builder time(IntRange timeRange) {
        return new TimeCheck.Builder(timeRange);
    }

    public static class Builder implements LootItemCondition.Builder {
        private Optional<Long> period = Optional.empty();
        private final IntRange value;

        public Builder(IntRange timeRange) {
            this.value = timeRange;
        }

        public TimeCheck.Builder setPeriod(long period) {
            this.period = Optional.of(period);
            return this;
        }

        public TimeCheck build() {
            return new TimeCheck(this.period, this.value);
        }
    }
}
