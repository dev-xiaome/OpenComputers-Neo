package net.minecraft.util.valueproviders;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

public abstract class IntProvider {
    private static final Codec<Either<Integer, IntProvider>> CONSTANT_OR_DISPATCH_CODEC = Codec.either(
        Codec.INT, BuiltInRegistries.INT_PROVIDER_TYPE.byNameCodec().dispatch(IntProvider::getType, IntProviderType::codec)
    );
    public static final Codec<IntProvider> CODEC = CONSTANT_OR_DISPATCH_CODEC.xmap(
        p_146543_ -> p_146543_.map(ConstantInt::of, p_146549_ -> (IntProvider)p_146549_),
        p_146541_ -> p_146541_.getType() == IntProviderType.CONSTANT ? Either.left(((ConstantInt)p_146541_).getValue()) : Either.right(p_146541_)
    );
    public static final Codec<IntProvider> NON_NEGATIVE_CODEC = codec(0, Integer.MAX_VALUE);
    public static final Codec<IntProvider> POSITIVE_CODEC = codec(1, Integer.MAX_VALUE);

    /**
     * Creates a codec for an IntProvider that only accepts numbers in the given range.
     */
    public static Codec<IntProvider> codec(int minInclusive, int maxInclusive) {
        return validateCodec(minInclusive, maxInclusive, CODEC);
    }

    public static <T extends IntProvider> Codec<T> validateCodec(int min, int max, Codec<T> codec) {
        return codec.validate(p_337695_ -> validate(min, max, p_337695_));
    }

    private static <T extends IntProvider> DataResult<T> validate(int min, int max, T provider) {
        if (provider.getMinValue() < min) {
            return DataResult.error(() -> "Value provider too low: " + min + " [" + provider.getMinValue() + "-" + provider.getMaxValue() + "]");
        } else {
            return provider.getMaxValue() > max
                ? DataResult.error(() -> "Value provider too high: " + max + " [" + provider.getMinValue() + "-" + provider.getMaxValue() + "]")
                : DataResult.success(provider);
        }
    }

    public abstract int sample(RandomSource random);

    public abstract int getMinValue();

    public abstract int getMaxValue();

    public abstract IntProviderType<?> getType();
}
