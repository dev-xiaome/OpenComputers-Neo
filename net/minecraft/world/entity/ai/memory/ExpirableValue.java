package net.minecraft.world.entity.ai.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.util.VisibleForDebug;

public class ExpirableValue<T> {
    private final T value;
    private long timeToLive;

    public ExpirableValue(T value, long timeToLive) {
        this.value = value;
        this.timeToLive = timeToLive;
    }

    public void tick() {
        if (this.canExpire()) {
            this.timeToLive--;
        }
    }

    public static <T> ExpirableValue<T> of(T value) {
        return new ExpirableValue<>(value, Long.MAX_VALUE);
    }

    public static <T> ExpirableValue<T> of(T value, long timeToLive) {
        return new ExpirableValue<>(value, timeToLive);
    }

    public long getTimeToLive() {
        return this.timeToLive;
    }

    public T getValue() {
        return this.value;
    }

    public boolean hasExpired() {
        return this.timeToLive <= 0L;
    }

    @Override
    public String toString() {
        return this.value + (this.canExpire() ? " (ttl: " + this.timeToLive + ")" : "");
    }

    @VisibleForDebug
    public boolean canExpire() {
        return this.timeToLive != Long.MAX_VALUE;
    }

    public static <T> Codec<ExpirableValue<T>> codec(Codec<T> valueCodec) {
        return RecordCodecBuilder.create(
            p_337791_ -> p_337791_.group(
                        valueCodec.fieldOf("value").forGetter(p_148193_ -> p_148193_.value),
                        Codec.LONG
                            .lenientOptionalFieldOf("ttl")
                            .forGetter(p_148187_ -> p_148187_.canExpire() ? Optional.of(p_148187_.timeToLive) : Optional.empty())
                    )
                    .apply(p_337791_, (p_148189_, p_148190_) -> new ExpirableValue<>(p_148189_, p_148190_.orElse(Long.MAX_VALUE)))
        );
    }
}
