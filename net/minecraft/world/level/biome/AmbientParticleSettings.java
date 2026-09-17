package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

public class AmbientParticleSettings {
    public static final Codec<AmbientParticleSettings> CODEC = RecordCodecBuilder.create(
        p_47423_ -> p_47423_.group(
                    ParticleTypes.CODEC.fieldOf("options").forGetter(p_151654_ -> p_151654_.options),
                    Codec.FLOAT.fieldOf("probability").forGetter(p_151652_ -> p_151652_.probability)
                )
                .apply(p_47423_, AmbientParticleSettings::new)
    );
    private final ParticleOptions options;
    private final float probability;

    public AmbientParticleSettings(ParticleOptions options, float probability) {
        this.options = options;
        this.probability = probability;
    }

    public ParticleOptions getOptions() {
        return this.options;
    }

    public boolean canSpawn(RandomSource random) {
        return random.nextFloat() <= this.probability;
    }
}
