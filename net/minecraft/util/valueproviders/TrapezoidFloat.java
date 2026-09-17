package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.RandomSource;

public class TrapezoidFloat extends FloatProvider {
    public static final MapCodec<TrapezoidFloat> CODEC = RecordCodecBuilder.<TrapezoidFloat>mapCodec(
            p_146578_ -> p_146578_.group(
                        Codec.FLOAT.fieldOf("min").forGetter(p_146588_ -> p_146588_.min),
                        Codec.FLOAT.fieldOf("max").forGetter(p_146586_ -> p_146586_.max),
                        Codec.FLOAT.fieldOf("plateau").forGetter(p_146583_ -> p_146583_.plateau)
                    )
                    .apply(p_146578_, TrapezoidFloat::new)
        )
        .validate(
            p_274953_ -> {
                if (p_274953_.max < p_274953_.min) {
                    return DataResult.error(() -> "Max must be larger than min: [" + p_274953_.min + ", " + p_274953_.max + "]");
                } else {
                    return p_274953_.plateau > p_274953_.max - p_274953_.min
                        ? DataResult.error(() -> "Plateau can at most be the full span: [" + p_274953_.min + ", " + p_274953_.max + "]")
                        : DataResult.success(p_274953_);
                }
            }
        );
    private final float min;
    private final float max;
    private final float plateau;

    public static TrapezoidFloat of(float min, float max, float plateau) {
        return new TrapezoidFloat(min, max, plateau);
    }

    private TrapezoidFloat(float min, float max, float plateau) {
        this.min = min;
        this.max = max;
        this.plateau = plateau;
    }

    @Override
    public float sample(RandomSource random) {
        float f = this.max - this.min;
        float f1 = (f - this.plateau) / 2.0F;
        float f2 = f - f1;
        return this.min + random.nextFloat() * f2 + random.nextFloat() * f1;
    }

    @Override
    public float getMinValue() {
        return this.min;
    }

    @Override
    public float getMaxValue() {
        return this.max;
    }

    @Override
    public FloatProviderType<?> getType() {
        return FloatProviderType.TRAPEZOID;
    }

    @Override
    public String toString() {
        return "trapezoid(" + this.plateau + ") in [" + this.min + "-" + this.max + "]";
    }
}
