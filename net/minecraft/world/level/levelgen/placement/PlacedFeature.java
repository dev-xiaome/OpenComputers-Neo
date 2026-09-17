package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.apache.commons.lang3.mutable.MutableBoolean;

public record PlacedFeature(Holder<ConfiguredFeature<?, ?>> feature, List<PlacementModifier> placement) {
    public static final Codec<PlacedFeature> DIRECT_CODEC = RecordCodecBuilder.create(
        p_191788_ -> p_191788_.group(
                    ConfiguredFeature.CODEC.fieldOf("feature").forGetter(p_204928_ -> p_204928_.feature),
                    PlacementModifier.CODEC.listOf().fieldOf("placement").forGetter(p_191796_ -> p_191796_.placement)
                )
                .apply(p_191788_, PlacedFeature::new)
    );
    public static final Codec<Holder<PlacedFeature>> CODEC = RegistryFileCodec.create(Registries.PLACED_FEATURE, DIRECT_CODEC);
    public static final Codec<HolderSet<PlacedFeature>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.PLACED_FEATURE, DIRECT_CODEC);
    public static final Codec<List<HolderSet<PlacedFeature>>> LIST_OF_LISTS_CODEC = RegistryCodecs.homogeneousList(
            Registries.PLACED_FEATURE, DIRECT_CODEC, true
        )
        .listOf();

    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return this.placeWithContext(new PlacementContext(level, generator, Optional.empty()), random, pos);
    }

    public boolean placeWithBiomeCheck(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return this.placeWithContext(new PlacementContext(level, generator, Optional.of(this)), random, pos);
    }

    private boolean placeWithContext(PlacementContext context, RandomSource source, BlockPos pos) {
        Stream<BlockPos> stream = Stream.of(pos);

        for (PlacementModifier placementmodifier : this.placement) {
            stream = stream.flatMap(p_226376_ -> placementmodifier.getPositions(context, source, p_226376_));
        }

        ConfiguredFeature<?, ?> configuredfeature = this.feature.value();
        MutableBoolean mutableboolean = new MutableBoolean();
        stream.forEach(p_226367_ -> {
            if (configuredfeature.place(context.getLevel(), context.generator(), source, p_226367_)) {
                mutableboolean.setTrue();
            }
        });
        return mutableboolean.isTrue();
    }

    public Stream<ConfiguredFeature<?, ?>> getFeatures() {
        return this.feature.value().getFeatures();
    }

    @Override
    public String toString() {
        return "Placed " + this.feature;
    }
}
