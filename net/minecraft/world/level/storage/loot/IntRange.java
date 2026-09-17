package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/**
 * A possibly unbounded range of integers based on {@link LootContext}. Minimum and maximum are given in the form of {@link NumberProvider}s.
 * Minimum and maximum are both optional. If given, they are both inclusive.
 */
public class IntRange {
    private static final Codec<IntRange> RECORD_CODEC = RecordCodecBuilder.create(
        p_338119_ -> p_338119_.group(
                    NumberProviders.CODEC.optionalFieldOf("min").forGetter(p_297985_ -> Optional.ofNullable(p_297985_.min)),
                    NumberProviders.CODEC.optionalFieldOf("max").forGetter(p_297984_ -> Optional.ofNullable(p_297984_.max))
                )
                .apply(p_338119_, IntRange::new)
    );
    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, RECORD_CODEC)
        .xmap(p_297983_ -> p_297983_.map(IntRange::exact, Function.identity()), p_297982_ -> {
            OptionalInt optionalint = p_297982_.unpackExact();
            return optionalint.isPresent() ? Either.left(optionalint.getAsInt()) : Either.right(p_297982_);
        });
    @Nullable
    private final NumberProvider min;
    @Nullable
    private final NumberProvider max;
    private final IntRange.IntLimiter limiter;
    private final IntRange.IntChecker predicate;

    public Set<LootContextParam<?>> getReferencedContextParams() {
        Builder<LootContextParam<?>> builder = ImmutableSet.builder();
        if (this.min != null) {
            builder.addAll(this.min.getReferencedContextParams());
        }

        if (this.max != null) {
            builder.addAll(this.max.getReferencedContextParams());
        }

        return builder.build();
    }

    private IntRange(Optional<NumberProvider> min, Optional<NumberProvider> max) {
        this(min.orElse(null), max.orElse(null));
    }

    private IntRange(@Nullable NumberProvider min, @Nullable NumberProvider max) {
        this.min = min;
        this.max = max;
        if (min == null) {
            if (max == null) {
                this.limiter = (p_165050_, p_165051_) -> p_165051_;
                this.predicate = (p_165043_, p_165044_) -> true;
            } else {
                this.limiter = (p_165054_, p_165055_) -> Math.min(max.getInt(p_165054_), p_165055_);
                this.predicate = (p_165047_, p_165048_) -> p_165048_ <= max.getInt(p_165047_);
            }
        } else if (max == null) {
            this.limiter = (p_165033_, p_165034_) -> Math.max(min.getInt(p_165033_), p_165034_);
            this.predicate = (p_165019_, p_165020_) -> p_165020_ >= min.getInt(p_165019_);
        } else {
            this.limiter = (p_165038_, p_165039_) -> Mth.clamp(p_165039_, min.getInt(p_165038_), max.getInt(p_165038_));
            this.predicate = (p_165024_, p_165025_) -> p_165025_ >= min.getInt(p_165024_) && p_165025_ <= max.getInt(p_165024_);
        }
    }

    /**
     * Create an IntRange that contains only exactly the given value.
     */
    public static IntRange exact(int exactValue) {
        ConstantValue constantvalue = ConstantValue.exactly((float)exactValue);
        return new IntRange(Optional.of(constantvalue), Optional.of(constantvalue));
    }

    /**
     * Create an IntRange that ranges from {@code min} to {@code max}, both inclusive.
     */
    public static IntRange range(int min, int max) {
        return new IntRange(Optional.of(ConstantValue.exactly((float)min)), Optional.of(ConstantValue.exactly((float)max)));
    }

    /**
     * Create an IntRange with the given minimum (inclusive) and no upper bound.
     */
    public static IntRange lowerBound(int min) {
        return new IntRange(Optional.of(ConstantValue.exactly((float)min)), Optional.empty());
    }

    /**
     * Create an IntRange with the given maximum (inclusive) and no lower bound.
     */
    public static IntRange upperBound(int max) {
        return new IntRange(Optional.empty(), Optional.of(ConstantValue.exactly((float)max)));
    }

    /**
     * Clamp the given value so that it falls within this IntRange.
     */
    public int clamp(LootContext lootContext, int value) {
        return this.limiter.apply(lootContext, value);
    }

    /**
     * Check whether the given value falls within this IntRange.
     */
    public boolean test(LootContext lootContext, int value) {
        return this.predicate.test(lootContext, value);
    }

    private OptionalInt unpackExact() {
        return Objects.equals(this.min, this.max)
                && this.min instanceof ConstantValue constantvalue
                && Math.floor((double)constantvalue.value()) == (double)constantvalue.value()
            ? OptionalInt.of((int)constantvalue.value())
            : OptionalInt.empty();
    }

    @FunctionalInterface
    interface IntChecker {
        boolean test(LootContext lootContext, int value);
    }

    @FunctionalInterface
    interface IntLimiter {
        int apply(LootContext lootContext, int value);
    }
}
