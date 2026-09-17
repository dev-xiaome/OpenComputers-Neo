package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class HugeMushroomFeatureConfiguration implements FeatureConfiguration {
    public static final Codec<HugeMushroomFeatureConfiguration> CODEC = RecordCodecBuilder.create(
        p_67751_ -> p_67751_.group(
                    BlockStateProvider.CODEC.fieldOf("cap_provider").forGetter(p_160943_ -> p_160943_.capProvider),
                    BlockStateProvider.CODEC.fieldOf("stem_provider").forGetter(p_160941_ -> p_160941_.stemProvider),
                    Codec.INT.fieldOf("foliage_radius").orElse(2).forGetter(p_160939_ -> p_160939_.foliageRadius)
                )
                .apply(p_67751_, HugeMushroomFeatureConfiguration::new)
    );
    public final BlockStateProvider capProvider;
    public final BlockStateProvider stemProvider;
    public final int foliageRadius;

    public HugeMushroomFeatureConfiguration(BlockStateProvider capProvider, BlockStateProvider stemProvider, int foliageRadius) {
        this.capProvider = capProvider;
        this.stemProvider = stemProvider;
        this.foliageRadius = foliageRadius;
    }
}
