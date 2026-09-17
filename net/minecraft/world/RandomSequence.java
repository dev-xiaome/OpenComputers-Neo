package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class RandomSequence {
    public static final Codec<RandomSequence> CODEC = RecordCodecBuilder.create(
        p_287586_ -> p_287586_.group(XoroshiroRandomSource.CODEC.fieldOf("source").forGetter(p_287757_ -> p_287757_.source))
                .apply(p_287586_, RandomSequence::new)
    );
    private final XoroshiroRandomSource source;

    public RandomSequence(XoroshiroRandomSource source) {
        this.source = source;
    }

    public RandomSequence(long seed, ResourceLocation location) {
        this(createSequence(seed, Optional.of(location)));
    }

    public RandomSequence(long seed, Optional<ResourceLocation> location) {
        this(createSequence(seed, location));
    }

    private static XoroshiroRandomSource createSequence(long seed, Optional<ResourceLocation> location) {
        RandomSupport.Seed128bit randomsupport$seed128bit = RandomSupport.upgradeSeedTo128bitUnmixed(seed);
        if (location.isPresent()) {
            randomsupport$seed128bit = randomsupport$seed128bit.xor(seedForKey(location.get()));
        }

        return new XoroshiroRandomSource(randomsupport$seed128bit.mixed());
    }

    public static RandomSupport.Seed128bit seedForKey(ResourceLocation key) {
        return RandomSupport.seedFromHashOf(key.toString());
    }

    public RandomSource random() {
        return this.source;
    }
}
