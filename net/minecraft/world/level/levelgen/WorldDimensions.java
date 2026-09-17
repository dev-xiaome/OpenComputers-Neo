package net.minecraft.world.level.levelgen;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.PrimaryLevelData;

public record WorldDimensions(Map<ResourceKey<LevelStem>, LevelStem> dimensions) {
    public static final MapCodec<WorldDimensions> CODEC = RecordCodecBuilder.mapCodec(
        p_325905_ -> p_325905_.group(
                    // FORGE: Fix MC-197860
                    new net.neoforged.neoforge.common.LenientUnboundedMapCodec<>(ResourceKey.codec(Registries.LEVEL_STEM), LevelStem.CODEC).fieldOf("dimensions").forGetter(WorldDimensions::dimensions)
                )
                .apply(p_325905_, p_325905_.stable(WorldDimensions::new))
    );
    private static final Set<ResourceKey<LevelStem>> BUILTIN_ORDER = ImmutableSet.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END);
    private static final int VANILLA_DIMENSION_COUNT = BUILTIN_ORDER.size();

    public WorldDimensions(Map<ResourceKey<LevelStem>, LevelStem> dimensions) {
        LevelStem levelstem = dimensions.get(LevelStem.OVERWORLD);
        if (levelstem == null) {
            throw new IllegalStateException("Overworld settings missing");
        } else {
            this.dimensions = dimensions;
        }
    }

    public WorldDimensions(Registry<LevelStem> p_251356_) {
        this(p_251356_.holders().collect(Collectors.toMap(Holder.Reference::key, Holder.Reference::value)));
    }

    public static Stream<ResourceKey<LevelStem>> keysInOrder(Stream<ResourceKey<LevelStem>> stemKeys) {
        return Stream.concat(BUILTIN_ORDER.stream(), stemKeys.filter(p_251885_ -> !BUILTIN_ORDER.contains(p_251885_)));
    }

    public WorldDimensions replaceOverworldGenerator(RegistryAccess registryAccess, ChunkGenerator chunkGenerator) {
        Registry<DimensionType> registry = registryAccess.registryOrThrow(Registries.DIMENSION_TYPE);
        Map<ResourceKey<LevelStem>, LevelStem> map = withOverworld(registry, this.dimensions, chunkGenerator);
        return new WorldDimensions(map);
    }

    public static Map<ResourceKey<LevelStem>, LevelStem> withOverworld(
        Registry<DimensionType> dimensionTypeRegistry, Map<ResourceKey<LevelStem>, LevelStem> stemMap, ChunkGenerator chunkGenerator
    ) {
        LevelStem levelstem = stemMap.get(LevelStem.OVERWORLD);
        Holder<DimensionType> holder = (Holder<DimensionType>)(levelstem == null
            ? dimensionTypeRegistry.getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD)
            : levelstem.type());
        return withOverworld(stemMap, holder, chunkGenerator);
    }

    public static Map<ResourceKey<LevelStem>, LevelStem> withOverworld(
        Map<ResourceKey<LevelStem>, LevelStem> stemMap, Holder<DimensionType> dimensionType, ChunkGenerator chunkGenerator
    ) {
        Builder<ResourceKey<LevelStem>, LevelStem> builder = ImmutableMap.builder();
        builder.putAll(stemMap);
        builder.put(LevelStem.OVERWORLD, new LevelStem(dimensionType, chunkGenerator));
        return builder.buildKeepingLast();
    }

    public ChunkGenerator overworld() {
        LevelStem levelstem = this.dimensions.get(LevelStem.OVERWORLD);
        if (levelstem == null) {
            throw new IllegalStateException("Overworld settings missing");
        } else {
            return levelstem.generator();
        }
    }

    public Optional<LevelStem> get(ResourceKey<LevelStem> stemKey) {
        return Optional.ofNullable(this.dimensions.get(stemKey));
    }

    public ImmutableSet<ResourceKey<Level>> levels() {
        return this.dimensions().keySet().stream().map(Registries::levelStemToLevel).collect(ImmutableSet.toImmutableSet());
    }

    public boolean isDebug() {
        return this.overworld() instanceof DebugLevelSource;
    }

    private static PrimaryLevelData.SpecialWorldProperty specialWorldProperty(Registry<LevelStem> stemRegistry) {
        return stemRegistry.getOptional(LevelStem.OVERWORLD).map(p_251481_ -> {
            ChunkGenerator chunkgenerator = p_251481_.generator();
            if (chunkgenerator instanceof DebugLevelSource) {
                return PrimaryLevelData.SpecialWorldProperty.DEBUG;
            } else {
                return chunkgenerator instanceof FlatLevelSource ? PrimaryLevelData.SpecialWorldProperty.FLAT : PrimaryLevelData.SpecialWorldProperty.NONE;
            }
        }).orElse(PrimaryLevelData.SpecialWorldProperty.NONE);
    }

    static Lifecycle checkStability(ResourceKey<LevelStem> key, LevelStem stem) {
        return isVanillaLike(key, stem) ? Lifecycle.stable() : Lifecycle.experimental();
    }

    private static boolean isVanillaLike(ResourceKey<LevelStem> key, LevelStem stem) {
        if (key == LevelStem.OVERWORLD) {
            return isStableOverworld(stem);
        } else if (key == LevelStem.NETHER) {
            return isStableNether(stem);
        } else {
            return key == LevelStem.END ? isStableEnd(stem) : false;
        }
    }

    private static boolean isStableOverworld(LevelStem levelStem) {
        Holder<DimensionType> holder = levelStem.type();
        if (!holder.is(BuiltinDimensionTypes.OVERWORLD) && !holder.is(BuiltinDimensionTypes.OVERWORLD_CAVES)) {
            return false;
        } else {
            if (levelStem.generator().getBiomeSource() instanceof MultiNoiseBiomeSource multinoisebiomesource
                && !multinoisebiomesource.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
                return false;
            }

            return true;
        }
    }

    private static boolean isStableNether(LevelStem levelStem) {
        return levelStem.type().is(BuiltinDimensionTypes.NETHER)
            && levelStem.generator() instanceof NoiseBasedChunkGenerator noisebasedchunkgenerator
            && noisebasedchunkgenerator.stable(NoiseGeneratorSettings.NETHER)
            && noisebasedchunkgenerator.getBiomeSource() instanceof MultiNoiseBiomeSource multinoisebiomesource
            && multinoisebiomesource.stable(MultiNoiseBiomeSourceParameterLists.NETHER);
    }

    private static boolean isStableEnd(LevelStem levelStem) {
        return levelStem.type().is(BuiltinDimensionTypes.END)
            && levelStem.generator() instanceof NoiseBasedChunkGenerator noisebasedchunkgenerator
            && noisebasedchunkgenerator.stable(NoiseGeneratorSettings.END)
            && noisebasedchunkgenerator.getBiomeSource() instanceof TheEndBiomeSource;
    }

    public WorldDimensions.Complete bake(Registry<LevelStem> stemRegistry) {
        Stream<ResourceKey<LevelStem>> stream = Stream.concat(stemRegistry.registryKeySet().stream(), this.dimensions.keySet().stream()).distinct();

        record Entry(ResourceKey<LevelStem> key, LevelStem value) {
            RegistrationInfo registrationInfo() {
                return new RegistrationInfo(Optional.empty(), WorldDimensions.checkStability(this.key, this.value));
            }
        }

        List<Entry> list = new ArrayList<>();
        keysInOrder(stream)
            .forEach(
                p_248571_ -> stemRegistry.getOptional((ResourceKey<LevelStem>)p_248571_)
                        .or(() -> Optional.ofNullable(this.dimensions.get(p_248571_)))
                        .ifPresent(p_250263_ -> list.add(new Entry(p_248571_, p_250263_)))
            );
        Lifecycle lifecycle = list.size() == VANILLA_DIMENSION_COUNT ? Lifecycle.stable() : Lifecycle.experimental();
        WritableRegistry<LevelStem> writableregistry = new MappedRegistry<>(Registries.LEVEL_STEM, lifecycle);
        list.forEach(p_325908_ -> writableregistry.register(p_325908_.key, p_325908_.value, p_325908_.registrationInfo()));
        Registry<LevelStem> registry = writableregistry.freeze();
        PrimaryLevelData.SpecialWorldProperty primaryleveldata$specialworldproperty = specialWorldProperty(registry);
        return new WorldDimensions.Complete(registry.freeze(), primaryleveldata$specialworldproperty);
    }

    public static record Complete(Registry<LevelStem> dimensions, PrimaryLevelData.SpecialWorldProperty specialWorldProperty) {
        public Lifecycle lifecycle() {
            return this.dimensions.registryLifecycle();
        }

        public RegistryAccess.Frozen dimensionsRegistryAccess() {
            return new RegistryAccess.ImmutableRegistryAccess(List.of(this.dimensions)).freeze();
        }
    }
}
