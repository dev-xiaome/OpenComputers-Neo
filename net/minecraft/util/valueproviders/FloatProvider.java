package net.minecraft.util.valueproviders;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;

public abstract class FloatProvider implements SampledFloat {
    private static final Codec<Either<Float, FloatProvider>> CONSTANT_OR_DISPATCH_CODEC = Codec.either(
        Codec.FLOAT, BuiltInRegistries.FLOAT_PROVIDER_TYPE.byNameCodec().dispatch(FloatProvider::getType, FloatProviderType::codec)
    );
    public static final Codec<FloatProvider> CODEC = CONSTANT_OR_DISPATCH_CODEC.xmap(
        p_146515_ -> p_146515_.map(ConstantFloat::of, p_146518_ -> (FloatProvider)p_146518_),
        p_146513_ -> p_146513_.getType() == FloatProviderType.CONSTANT ? Either.left(((ConstantFloat)p_146513_).getValue()) : Either.right(p_146513_)
    );

    /**
     * Creates a codec for a FloatProvider that only accepts numbers in the given range.
     */
    public static Codec<FloatProvider> codec(float minInclusive, float maxInclusive) {
        return CODEC.validate(
            p_274942_ -> {
                if (p_274942_.getMinValue() < minInclusive) {
                    return DataResult.error(() -> "Value provider too low: " + minInclusive + " [" + p_274942_.getMinValue() + "-" + p_274942_.getMaxValue() + "]");
                } else {
                    return p_274942_.getMaxValue() > maxInclusive
                        ? DataResult.error(() -> "Value provider too high: " + maxInclusive + " [" + p_274942_.getMinValue() + "-" + p_274942_.getMaxValue() + "]")
                        : DataResult.success(p_274942_);
                }
            }
        );
    }

    public abstract float getMinValue();

    public abstract float getMaxValue();

    public abstract FloatProviderType<?> getType();
}
