package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record RemoveBinomial(LevelBasedValue chance) implements EnchantmentValueEffect {
    public static final MapCodec<RemoveBinomial> CODEC = RecordCodecBuilder.mapCodec(
        p_344917_ -> p_344917_.group(LevelBasedValue.CODEC.fieldOf("chance").forGetter(RemoveBinomial::chance)).apply(p_344917_, RemoveBinomial::new)
    );

    @Override
    public float process(int enchantmentLevel, RandomSource random, float value) {
        float f = this.chance.calculate(enchantmentLevel);
        int i = 0;

        for (int j = 0; (float)j < value; j++) {
            if (random.nextFloat() < f) {
                i++;
            }
        }

        return value - (float)i;
    }

    @Override
    public MapCodec<RemoveBinomial> codec() {
        return CODEC;
    }
}
