package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;

public class CheckerboardColumnBiomeSource extends BiomeSource {
    public static final MapCodec<CheckerboardColumnBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
        p_48244_ -> p_48244_.group(
                    Biome.LIST_CODEC.fieldOf("biomes").forGetter(p_204246_ -> p_204246_.allowedBiomes),
                    Codec.intRange(0, 62).fieldOf("scale").orElse(2).forGetter(p_151788_ -> p_151788_.size)
                )
                .apply(p_48244_, CheckerboardColumnBiomeSource::new)
    );
    private final HolderSet<Biome> allowedBiomes;
    private final int bitShift;
    private final int size;

    public CheckerboardColumnBiomeSource(HolderSet<Biome> allowedBiomes, int size) {
        this.allowedBiomes = allowedBiomes;
        this.bitShift = size + 2;
        this.size = size;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.allowedBiomes.stream();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.allowedBiomes.get(Math.floorMod((x >> this.bitShift) + (z >> this.bitShift), this.allowedBiomes.size()));
    }
}
