package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.valueproviders.IntProvider;

public class ColumnFeatureConfiguration implements FeatureConfiguration {
    public static final Codec<ColumnFeatureConfiguration> CODEC = RecordCodecBuilder.create(
        p_67563_ -> p_67563_.group(
                    IntProvider.codec(0, 3).fieldOf("reach").forGetter(p_160722_ -> p_160722_.reach),
                    IntProvider.codec(1, 10).fieldOf("height").forGetter(p_160719_ -> p_160719_.height)
                )
                .apply(p_67563_, ColumnFeatureConfiguration::new)
    );
    private final IntProvider reach;
    private final IntProvider height;

    public ColumnFeatureConfiguration(IntProvider reach, IntProvider height) {
        this.reach = reach;
        this.height = height;
    }

    public IntProvider reach() {
        return this.reach;
    }

    public IntProvider height() {
        return this.height;
    }
}
