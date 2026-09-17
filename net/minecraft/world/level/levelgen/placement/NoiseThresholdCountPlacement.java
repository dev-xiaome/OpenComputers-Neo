package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public class NoiseThresholdCountPlacement extends RepeatingPlacement {
    public static final MapCodec<NoiseThresholdCountPlacement> CODEC = RecordCodecBuilder.mapCodec(
        p_191761_ -> p_191761_.group(
                    Codec.DOUBLE.fieldOf("noise_level").forGetter(p_191771_ -> p_191771_.noiseLevel),
                    Codec.INT.fieldOf("below_noise").forGetter(p_191769_ -> p_191769_.belowNoise),
                    Codec.INT.fieldOf("above_noise").forGetter(p_191763_ -> p_191763_.aboveNoise)
                )
                .apply(p_191761_, NoiseThresholdCountPlacement::new)
    );
    private final double noiseLevel;
    private final int belowNoise;
    private final int aboveNoise;

    private NoiseThresholdCountPlacement(double noiseLevel, int belowNoise, int aboveNoise) {
        this.noiseLevel = noiseLevel;
        this.belowNoise = belowNoise;
        this.aboveNoise = aboveNoise;
    }

    public static NoiseThresholdCountPlacement of(double noiseLevel, int belowNoise, int aboveNoise) {
        return new NoiseThresholdCountPlacement(noiseLevel, belowNoise, aboveNoise);
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        double d0 = Biome.BIOME_INFO_NOISE.getValue((double)pos.getX() / 200.0, (double)pos.getZ() / 200.0, false);
        return d0 < this.noiseLevel ? this.belowNoise : this.aboveNoise;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.NOISE_THRESHOLD_COUNT;
    }
}
