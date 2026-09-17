package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

public class BiomeGenerationSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BiomeGenerationSettings EMPTY = new BiomeGenerationSettings(ImmutableMap.of(), ImmutableList.of());
    public static final MapCodec<BiomeGenerationSettings> CODEC = RecordCodecBuilder.mapCodec(
        p_186655_ -> p_186655_.group(
                    Codec.simpleMap(
                            GenerationStep.Carving.CODEC,
                            ConfiguredWorldCarver.LIST_CODEC.promotePartial(Util.prefix("Carver: ", LOGGER::error)),
                            StringRepresentable.keys(GenerationStep.Carving.values())
                        )
                        .fieldOf("carvers")
                        .forGetter(p_186661_ -> p_186661_.carvers),
                    PlacedFeature.LIST_OF_LISTS_CODEC
                        .promotePartial(Util.prefix("Features: ", LOGGER::error))
                        .fieldOf("features")
                        .forGetter(p_186653_ -> p_186653_.features)
                )
                .apply(p_186655_, BiomeGenerationSettings::new)
    );
    private final Map<GenerationStep.Carving, HolderSet<ConfiguredWorldCarver<?>>> carvers;
    private final java.util.Set<GenerationStep.Carving> carversView;
    private final List<HolderSet<PlacedFeature>> features;
    private final Supplier<List<ConfiguredFeature<?, ?>>> flowerFeatures;
    private final Supplier<Set<PlacedFeature>> featureSet;

    public BiomeGenerationSettings(Map<GenerationStep.Carving, HolderSet<ConfiguredWorldCarver<?>>> p_carvers, List<HolderSet<PlacedFeature>> features) {
        this.carvers = p_carvers;
        this.features = features;
        this.flowerFeatures = Suppliers.memoize(
            () -> features.stream()
                    .flatMap(HolderSet::stream)
                    .map(Holder::value)
                    .flatMap(PlacedFeature::getFeatures)
                    .filter(p_186657_ -> p_186657_.feature() == Feature.FLOWER)
                    .collect(ImmutableList.toImmutableList())
        );
        this.featureSet = Suppliers.memoize(() -> features.stream().flatMap(HolderSet::stream).map(Holder::value).collect(Collectors.toSet()));
        this.carversView = java.util.Collections.unmodifiableSet(carvers.keySet());
    }

    public Iterable<Holder<ConfiguredWorldCarver<?>>> getCarvers(GenerationStep.Carving step) {
        return Objects.requireNonNullElseGet(this.carvers.get(step), List::of);
    }

    public java.util.Set<GenerationStep.Carving> getCarvingStages() {
         return this.carversView;
    }

    public List<ConfiguredFeature<?, ?>> getFlowerFeatures() {
        return this.flowerFeatures.get();
    }

    public List<HolderSet<PlacedFeature>> features() {
        return this.features;
    }

    public boolean hasFeature(PlacedFeature feature) {
        return this.featureSet.get().contains(feature);
    }

    public static class Builder extends BiomeGenerationSettings.PlainBuilder {
        private final HolderGetter<PlacedFeature> placedFeatures;
        private final HolderGetter<ConfiguredWorldCarver<?>> worldCarvers;

        public Builder(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
            this.placedFeatures = placedFeatures;
            this.worldCarvers = worldCarvers;
        }

        public BiomeGenerationSettings.Builder addFeature(GenerationStep.Decoration decoration, ResourceKey<PlacedFeature> feature) {
            this.addFeature(decoration.ordinal(), this.placedFeatures.getOrThrow(feature));
            return this;
        }

        public BiomeGenerationSettings.Builder addCarver(GenerationStep.Carving carving, ResourceKey<ConfiguredWorldCarver<?>> carver) {
            this.addCarver(carving, this.worldCarvers.getOrThrow(carver));
            return this;
        }
    }

    public static class PlainBuilder {
        protected final Map<GenerationStep.Carving, List<Holder<ConfiguredWorldCarver<?>>>> carvers = Maps.newLinkedHashMap();
        protected final List<List<Holder<PlacedFeature>>> features = Lists.newArrayList();

        public BiomeGenerationSettings.PlainBuilder addFeature(GenerationStep.Decoration decoration, Holder<PlacedFeature> feature) {
            return this.addFeature(decoration.ordinal(), feature);
        }

        public BiomeGenerationSettings.PlainBuilder addFeature(int step, Holder<PlacedFeature> feature) {
            this.addFeatureStepsUpTo(step);
            this.features.get(step).add(feature);
            return this;
        }

        public BiomeGenerationSettings.PlainBuilder addCarver(GenerationStep.Carving carving, Holder<ConfiguredWorldCarver<?>> carver) {
            this.carvers.computeIfAbsent(carving, p_256199_ -> Lists.newArrayList()).add(carver);
            return this;
        }

        protected void addFeatureStepsUpTo(int step) {
            while (this.features.size() <= step) {
                this.features.add(Lists.newArrayList());
            }
        }

        public BiomeGenerationSettings build() {
            return new BiomeGenerationSettings(
                this.carvers.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, p_255831_ -> HolderSet.direct(p_255831_.getValue()))),
                this.features.stream().map(HolderSet::direct).collect(ImmutableList.toImmutableList())
            );
        }
    }
}
