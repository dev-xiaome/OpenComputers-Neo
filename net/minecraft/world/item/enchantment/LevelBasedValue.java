package net.minecraft.world.item.enchantment;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;

public interface LevelBasedValue {
    Codec<LevelBasedValue> DISPATCH_CODEC = BuiltInRegistries.ENCHANTMENT_LEVEL_BASED_VALUE_TYPE
        .byNameCodec()
        .dispatch(LevelBasedValue::codec, p_346236_ -> p_346236_);
    Codec<LevelBasedValue> CODEC = Codec.either(LevelBasedValue.Constant.CODEC, DISPATCH_CODEC)
        .xmap(
            p_345066_ -> p_345066_.map(p_345590_ -> (LevelBasedValue)p_345590_, p_346044_ -> (LevelBasedValue)p_346044_),
            p_346307_ -> p_346307_ instanceof LevelBasedValue.Constant levelbasedvalue$constant
                    ? Either.left(levelbasedvalue$constant)
                    : Either.right(p_346307_)
        );

    static MapCodec<? extends LevelBasedValue> bootstrap(Registry<MapCodec<? extends LevelBasedValue>> registry) {
        Registry.register(registry, "clamped", LevelBasedValue.Clamped.CODEC);
        Registry.register(registry, "fraction", LevelBasedValue.Fraction.CODEC);
        Registry.register(registry, "levels_squared", LevelBasedValue.LevelsSquared.CODEC);
        Registry.register(registry, "linear", LevelBasedValue.Linear.CODEC);
        return Registry.register(registry, "lookup", LevelBasedValue.Lookup.CODEC);
    }

    static LevelBasedValue.Constant constant(float value) {
        return new LevelBasedValue.Constant(value);
    }

    static LevelBasedValue.Linear perLevel(float base, float perLevelAfterFirst) {
        return new LevelBasedValue.Linear(base, perLevelAfterFirst);
    }

    static LevelBasedValue.Linear perLevel(float p_perLevel) {
        return perLevel(p_perLevel, p_perLevel);
    }

    static LevelBasedValue.Lookup lookup(List<Float> values, LevelBasedValue fallback) {
        return new LevelBasedValue.Lookup(values, fallback);
    }

    float calculate(int level);

    MapCodec<? extends LevelBasedValue> codec();

    public static record Clamped(LevelBasedValue value, float min, float max) implements LevelBasedValue {
        public static final MapCodec<LevelBasedValue.Clamped> CODEC = RecordCodecBuilder.<LevelBasedValue.Clamped>mapCodec(
                p_345501_ -> p_345501_.group(
                            LevelBasedValue.CODEC.fieldOf("value").forGetter(LevelBasedValue.Clamped::value),
                            Codec.FLOAT.fieldOf("min").forGetter(LevelBasedValue.Clamped::min),
                            Codec.FLOAT.fieldOf("max").forGetter(LevelBasedValue.Clamped::max)
                        )
                        .apply(p_345501_, LevelBasedValue.Clamped::new)
            )
            .validate(
                p_345949_ -> p_345949_.max <= p_345949_.min
                        ? DataResult.error(() -> "Max must be larger than min, min: " + p_345949_.min + ", max: " + p_345949_.max)
                        : DataResult.success(p_345949_)
            );

        @Override
        public float calculate(int level) {
            return Mth.clamp(this.value.calculate(level), this.min, this.max);
        }

        @Override
        public MapCodec<LevelBasedValue.Clamped> codec() {
            return CODEC;
        }
    }

    public static record Constant(float value) implements LevelBasedValue {
        public static final Codec<LevelBasedValue.Constant> CODEC = Codec.FLOAT.xmap(LevelBasedValue.Constant::new, LevelBasedValue.Constant::value);
        public static final MapCodec<LevelBasedValue.Constant> TYPED_CODEC = RecordCodecBuilder.mapCodec(
            p_344772_ -> p_344772_.group(Codec.FLOAT.fieldOf("value").forGetter(LevelBasedValue.Constant::value))
                    .apply(p_344772_, LevelBasedValue.Constant::new)
        );

        @Override
        public float calculate(int level) {
            return this.value;
        }

        @Override
        public MapCodec<LevelBasedValue.Constant> codec() {
            return TYPED_CODEC;
        }
    }

    public static record Fraction(LevelBasedValue numerator, LevelBasedValue denominator) implements LevelBasedValue {
        public static final MapCodec<LevelBasedValue.Fraction> CODEC = RecordCodecBuilder.mapCodec(
            p_344815_ -> p_344815_.group(
                        LevelBasedValue.CODEC.fieldOf("numerator").forGetter(LevelBasedValue.Fraction::numerator),
                        LevelBasedValue.CODEC.fieldOf("denominator").forGetter(LevelBasedValue.Fraction::denominator)
                    )
                    .apply(p_344815_, LevelBasedValue.Fraction::new)
        );

        @Override
        public float calculate(int level) {
            float f = this.denominator.calculate(level);
            return f == 0.0F ? 0.0F : this.numerator.calculate(level) / f;
        }

        @Override
        public MapCodec<LevelBasedValue.Fraction> codec() {
            return CODEC;
        }
    }

    public static record LevelsSquared(float added) implements LevelBasedValue {
        public static final MapCodec<LevelBasedValue.LevelsSquared> CODEC = RecordCodecBuilder.mapCodec(
            p_345289_ -> p_345289_.group(Codec.FLOAT.fieldOf("added").forGetter(LevelBasedValue.LevelsSquared::added))
                    .apply(p_345289_, LevelBasedValue.LevelsSquared::new)
        );

        @Override
        public float calculate(int level) {
            return (float)Mth.square(level) + this.added;
        }

        @Override
        public MapCodec<LevelBasedValue.LevelsSquared> codec() {
            return CODEC;
        }
    }

    public static record Linear(float base, float perLevelAboveFirst) implements LevelBasedValue {
        public static final MapCodec<LevelBasedValue.Linear> CODEC = RecordCodecBuilder.mapCodec(
            p_346144_ -> p_346144_.group(
                        Codec.FLOAT.fieldOf("base").forGetter(LevelBasedValue.Linear::base),
                        Codec.FLOAT.fieldOf("per_level_above_first").forGetter(LevelBasedValue.Linear::perLevelAboveFirst)
                    )
                    .apply(p_346144_, LevelBasedValue.Linear::new)
        );

        @Override
        public float calculate(int level) {
            return this.base + this.perLevelAboveFirst * (float)(level - 1);
        }

        @Override
        public MapCodec<LevelBasedValue.Linear> codec() {
            return CODEC;
        }
    }

    public static record Lookup(List<Float> values, LevelBasedValue fallback) implements LevelBasedValue {
        public static final MapCodec<LevelBasedValue.Lookup> CODEC = RecordCodecBuilder.mapCodec(
            p_352084_ -> p_352084_.group(
                        Codec.FLOAT.listOf().fieldOf("values").forGetter(LevelBasedValue.Lookup::values),
                        LevelBasedValue.CODEC.fieldOf("fallback").forGetter(LevelBasedValue.Lookup::fallback)
                    )
                    .apply(p_352084_, LevelBasedValue.Lookup::new)
        );

        @Override
        public float calculate(int level) {
            return level <= this.values.size() ? this.values.get(level - 1) : this.fallback.calculate(level);
        }

        @Override
        public MapCodec<LevelBasedValue.Lookup> codec() {
            return CODEC;
        }
    }
}
