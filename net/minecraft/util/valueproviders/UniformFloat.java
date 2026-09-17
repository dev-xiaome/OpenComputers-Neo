package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class UniformFloat extends FloatProvider {
    public static final MapCodec<UniformFloat> CODEC = RecordCodecBuilder.<UniformFloat>mapCodec(
            p_146601_ -> p_146601_.group(
                        Codec.FLOAT.fieldOf("min_inclusive").forGetter(p_146612_ -> p_146612_.minInclusive),
                        Codec.FLOAT.fieldOf("max_exclusive").forGetter(p_146609_ -> p_146609_.maxExclusive)
                    )
                    .apply(p_146601_, UniformFloat::new)
        )
        .validate(
            p_274956_ -> p_274956_.maxExclusive <= p_274956_.minInclusive
                    ? DataResult.error(
                        () -> "Max must be larger than min, min_inclusive: " + p_274956_.minInclusive + ", max_exclusive: " + p_274956_.maxExclusive
                    )
                    : DataResult.success(p_274956_)
        );
    private final float minInclusive;
    private final float maxExclusive;

    private UniformFloat(float minInclusive, float maxExclusive) {
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
    }

    public static UniformFloat of(float minInclusive, float maxExclusive) {
        if (maxExclusive <= minInclusive) {
            throw new IllegalArgumentException("Max must exceed min");
        } else {
            return new UniformFloat(minInclusive, maxExclusive);
        }
    }

    @Override
    public float sample(RandomSource random) {
        return Mth.randomBetween(random, this.minInclusive, this.maxExclusive);
    }

    @Override
    public float getMinValue() {
        return this.minInclusive;
    }

    @Override
    public float getMaxValue() {
        return this.maxExclusive;
    }

    @Override
    public FloatProviderType<?> getType() {
        return FloatProviderType.UNIFORM;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxExclusive + "]";
    }
}
