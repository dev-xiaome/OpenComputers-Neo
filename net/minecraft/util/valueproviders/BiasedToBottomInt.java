package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.RandomSource;

public class BiasedToBottomInt extends IntProvider {
    public static final MapCodec<BiasedToBottomInt> CODEC = RecordCodecBuilder.<BiasedToBottomInt>mapCodec(
            p_146373_ -> p_146373_.group(
                        Codec.INT.fieldOf("min_inclusive").forGetter(p_146381_ -> p_146381_.minInclusive),
                        Codec.INT.fieldOf("max_inclusive").forGetter(p_146378_ -> p_146378_.maxInclusive)
                    )
                    .apply(p_146373_, BiasedToBottomInt::new)
        )
        .validate(
            p_274930_ -> p_274930_.maxInclusive < p_274930_.minInclusive
                    ? DataResult.error(
                        () -> "Max must be at least min, min_inclusive: " + p_274930_.minInclusive + ", max_inclusive: " + p_274930_.maxInclusive
                    )
                    : DataResult.success(p_274930_)
        );
    private final int minInclusive;
    private final int maxInclusive;

    private BiasedToBottomInt(int minInclusive, int maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static BiasedToBottomInt of(int minInclusive, int maxInclusive) {
        return new BiasedToBottomInt(minInclusive, maxInclusive);
    }

    @Override
    public int sample(RandomSource random) {
        return this.minInclusive + random.nextInt(random.nextInt(this.maxInclusive - this.minInclusive + 1) + 1);
    }

    @Override
    public int getMinValue() {
        return this.minInclusive;
    }

    @Override
    public int getMaxValue() {
        return this.maxInclusive;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.BIASED_TO_BOTTOM;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
