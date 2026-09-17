package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface AllOf {
    static <T, A extends T> MapCodec<A> codec(Codec<T> codec, Function<List<T>, A> getter, Function<A, List<T>> factory) {
        return RecordCodecBuilder.mapCodec(p_345790_ -> p_345790_.group(codec.listOf().fieldOf("effects").forGetter(factory)).apply(p_345790_, getter));
    }

    static AllOf.EntityEffects entityEffects(EnchantmentEntityEffect... effects) {
        return new AllOf.EntityEffects(List.of(effects));
    }

    static AllOf.LocationBasedEffects locationBasedEffects(EnchantmentLocationBasedEffect... effects) {
        return new AllOf.LocationBasedEffects(List.of(effects));
    }

    static AllOf.ValueEffects valueEffects(EnchantmentValueEffect... effects) {
        return new AllOf.ValueEffects(List.of(effects));
    }

    public static record EntityEffects(List<EnchantmentEntityEffect> effects) implements EnchantmentEntityEffect {
        public static final MapCodec<AllOf.EntityEffects> CODEC = AllOf.codec(
            EnchantmentEntityEffect.CODEC, AllOf.EntityEffects::new, AllOf.EntityEffects::effects
        );

        @Override
        public void apply(ServerLevel p_346093_, int p_345940_, EnchantedItemInUse p_344929_, Entity p_345319_, Vec3 p_345200_) {
            for (EnchantmentEntityEffect enchantmententityeffect : this.effects) {
                enchantmententityeffect.apply(p_346093_, p_345940_, p_344929_, p_345319_, p_345200_);
            }
        }

        @Override
        public MapCodec<AllOf.EntityEffects> codec() {
            return CODEC;
        }
    }

    public static record LocationBasedEffects(List<EnchantmentLocationBasedEffect> effects) implements EnchantmentLocationBasedEffect {
        public static final MapCodec<AllOf.LocationBasedEffects> CODEC = AllOf.codec(
            EnchantmentLocationBasedEffect.CODEC, AllOf.LocationBasedEffects::new, AllOf.LocationBasedEffects::effects
        );

        @Override
        public void onChangedBlock(ServerLevel p_345329_, int p_345154_, EnchantedItemInUse p_344984_, Entity p_345671_, Vec3 p_344781_, boolean p_345113_) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : this.effects) {
                enchantmentlocationbasedeffect.onChangedBlock(p_345329_, p_345154_, p_344984_, p_345671_, p_344781_, p_345113_);
            }
        }

        @Override
        public void onDeactivated(EnchantedItemInUse p_346024_, Entity p_346234_, Vec3 p_346036_, int p_345698_) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : this.effects) {
                enchantmentlocationbasedeffect.onDeactivated(p_346024_, p_346234_, p_346036_, p_345698_);
            }
        }

        @Override
        public MapCodec<AllOf.LocationBasedEffects> codec() {
            return CODEC;
        }
    }

    public static record ValueEffects(List<EnchantmentValueEffect> effects) implements EnchantmentValueEffect {
        public static final MapCodec<AllOf.ValueEffects> CODEC = AllOf.codec(EnchantmentValueEffect.CODEC, AllOf.ValueEffects::new, AllOf.ValueEffects::effects);

        @Override
        public float process(int p_345324_, RandomSource p_345137_, float p_344866_) {
            for (EnchantmentValueEffect enchantmentvalueeffect : this.effects) {
                p_344866_ = enchantmentvalueeffect.process(p_345324_, p_345137_, p_344866_);
            }

            return p_344866_;
        }

        @Override
        public MapCodec<AllOf.ValueEffects> codec() {
            return CODEC;
        }
    }
}
