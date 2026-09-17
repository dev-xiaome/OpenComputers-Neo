package net.minecraft.world.level.storage.loot.providers.number;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

/**
 * Generates a random number which is uniformly distributed between a minimum and a maximum.
 * Minimum and maximum are themselves NumberProviders.
 */
public record UniformGenerator(NumberProvider min, NumberProvider max) implements NumberProvider {
    public static final MapCodec<UniformGenerator> CODEC = RecordCodecBuilder.mapCodec(
        p_298748_ -> p_298748_.group(
                    NumberProviders.CODEC.fieldOf("min").forGetter(UniformGenerator::min),
                    NumberProviders.CODEC.fieldOf("max").forGetter(UniformGenerator::max)
                )
                .apply(p_298748_, UniformGenerator::new)
    );

    @Override
    public LootNumberProviderType getType() {
        return NumberProviders.UNIFORM;
    }

    public static UniformGenerator between(float min, float max) {
        return new UniformGenerator(ConstantValue.exactly(min), ConstantValue.exactly(max));
    }

    @Override
    public int getInt(LootContext lootContext) {
        return Mth.nextInt(lootContext.getRandom(), this.min.getInt(lootContext), this.max.getInt(lootContext));
    }

    @Override
    public float getFloat(LootContext lootContext) {
        return Mth.nextFloat(lootContext.getRandom(), this.min.getFloat(lootContext), this.max.getFloat(lootContext));
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Sets.union(this.min.getReferencedContextParams(), this.max.getReferencedContextParams());
    }
}
