package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record InclusiveRange<T extends Comparable<T>>(T minInclusive, T maxInclusive) {
    public static final Codec<InclusiveRange<Integer>> INT = codec(Codec.INT);

    public InclusiveRange(T minInclusive, T maxInclusive) {
        if (minInclusive.compareTo(maxInclusive) > 0) {
            throw new IllegalArgumentException("min_inclusive must be less than or equal to max_inclusive");
        } else {
            this.minInclusive = minInclusive;
            this.maxInclusive = maxInclusive;
        }
    }

    public InclusiveRange(T p_296125_) {
        this(p_296125_, p_296125_);
    }

    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> codec) {
        return ExtraCodecs.intervalCodec(
            codec, "min_inclusive", "max_inclusive", InclusiveRange::create, InclusiveRange::minInclusive, InclusiveRange::maxInclusive
        );
    }

    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> p_codec, T min, T max) {
        return codec(p_codec)
            .validate(
                p_274898_ -> {
                    if (p_274898_.minInclusive().compareTo(min) < 0) {
                        return DataResult.error(
                            () -> "Range limit too low, expected at least "
                                    + min
                                    + " ["
                                    + p_274898_.minInclusive()
                                    + "-"
                                    + p_274898_.maxInclusive()
                                    + "]"
                        );
                    } else {
                        return p_274898_.maxInclusive().compareTo(max) > 0
                            ? DataResult.error(
                                () -> "Range limit too high, expected at most "
                                        + max
                                        + " ["
                                        + p_274898_.minInclusive()
                                        + "-"
                                        + p_274898_.maxInclusive()
                                        + "]"
                            )
                            : DataResult.success(p_274898_);
                    }
                }
            );
    }

    public static <T extends Comparable<T>> DataResult<InclusiveRange<T>> create(T min, T max) {
        return min.compareTo(max) <= 0
            ? DataResult.success(new InclusiveRange<>(min, max))
            : DataResult.error(() -> "min_inclusive must be less than or equal to max_inclusive");
    }

    public boolean isValueInRange(T value) {
        return value.compareTo(this.minInclusive) >= 0 && value.compareTo(this.maxInclusive) <= 0;
    }

    public boolean contains(InclusiveRange<T> value) {
        return value.minInclusive().compareTo(this.minInclusive) >= 0 && value.maxInclusive.compareTo(this.maxInclusive) <= 0;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + ", " + this.maxInclusive + "]";
    }
}
