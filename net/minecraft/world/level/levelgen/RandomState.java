package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class RandomState {
    final PositionalRandomFactory random;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private final NoiseRouter router;
    private final Climate.Sampler sampler;
    private final SurfaceSystem surfaceSystem;
    private final PositionalRandomFactory aquiferRandom;
    private final PositionalRandomFactory oreRandom;
    private final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseIntances;
    private final Map<ResourceLocation, PositionalRandomFactory> positionalRandoms;

    public static RandomState create(HolderGetter.Provider registries, ResourceKey<NoiseGeneratorSettings> settingsKey, long levelSeed) {
        return create(registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey).value(), registries.lookupOrThrow(Registries.NOISE), levelSeed);
    }

    public static RandomState create(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersGetter, long levelSeed) {
        return new RandomState(settings, noiseParametersGetter, levelSeed);
    }

    private RandomState(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersGetter, final long levelSeed) {
        this.random = settings.getRandomSource().newInstance(levelSeed).forkPositional();
        this.noises = noiseParametersGetter;
        this.aquiferRandom = this.random.fromHashOf(ResourceLocation.withDefaultNamespace("aquifer")).forkPositional();
        this.oreRandom = this.random.fromHashOf(ResourceLocation.withDefaultNamespace("ore")).forkPositional();
        this.noiseIntances = new ConcurrentHashMap<>();
        this.positionalRandoms = new ConcurrentHashMap<>();
        this.surfaceSystem = new SurfaceSystem(this, settings.defaultBlock(), settings.seaLevel(), this.random);
        final boolean flag = settings.useLegacyRandomSource();

        class NoiseWiringHelper implements DensityFunction.Visitor {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private RandomSource newLegacyInstance(long seed) {
                return new LegacyRandomSource(levelSeed + seed);
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
                Holder<NormalNoise.NoiseParameters> holder = noiseHolder.noiseData();
                if (flag) {
                    if (holder.is(Noises.TEMPERATURE)) {
                        NormalNoise normalnoise3 = NormalNoise.createLegacyNetherBiome(
                            this.newLegacyInstance(0L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0)
                        );
                        return new DensityFunction.NoiseHolder(holder, normalnoise3);
                    }

                    if (holder.is(Noises.VEGETATION)) {
                        NormalNoise normalnoise2 = NormalNoise.createLegacyNetherBiome(
                            this.newLegacyInstance(1L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0)
                        );
                        return new DensityFunction.NoiseHolder(holder, normalnoise2);
                    }

                    if (holder.is(Noises.SHIFT)) {
                        NormalNoise normalnoise1 = NormalNoise.create(
                            RandomState.this.random.fromHashOf(Noises.SHIFT.location()), new NormalNoise.NoiseParameters(0, 0.0)
                        );
                        return new DensityFunction.NoiseHolder(holder, normalnoise1);
                    }
                }

                NormalNoise normalnoise = RandomState.this.getOrCreateNoise(holder.unwrapKey().orElseThrow());
                return new DensityFunction.NoiseHolder(holder, normalnoise);
            }

            private DensityFunction wrapNew(DensityFunction densityFunction) {
                if (densityFunction instanceof BlendedNoise blendednoise) {
                    RandomSource randomsource = flag
                        ? this.newLegacyInstance(0L)
                        : RandomState.this.random.fromHashOf(ResourceLocation.withDefaultNamespace("terrain"));
                    return blendednoise.withNewRandom(randomsource);
                } else {
                    return (DensityFunction)(densityFunction instanceof DensityFunctions.EndIslandDensityFunction
                        ? new DensityFunctions.EndIslandDensityFunction(levelSeed)
                        : densityFunction);
                }
            }

            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
                return this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
            }
        }

        this.router = settings.noiseRouter().mapAll(new NoiseWiringHelper());
        DensityFunction.Visitor densityfunction$visitor = new DensityFunction.Visitor() {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private DensityFunction wrapNew(DensityFunction p_249732_) {
                if (p_249732_ instanceof DensityFunctions.HolderHolder densityfunctions$holderholder) {
                    return densityfunctions$holderholder.function().value();
                } else {
                    return p_249732_ instanceof DensityFunctions.Marker densityfunctions$marker ? densityfunctions$marker.wrapped() : p_249732_;
                }
            }

            @Override
            public DensityFunction apply(DensityFunction p_248616_) {
                return this.wrapped.computeIfAbsent(p_248616_, this::wrapNew);
            }
        };
        this.sampler = new Climate.Sampler(
            this.router.temperature().mapAll(densityfunction$visitor),
            this.router.vegetation().mapAll(densityfunction$visitor),
            this.router.continents().mapAll(densityfunction$visitor),
            this.router.erosion().mapAll(densityfunction$visitor),
            this.router.depth().mapAll(densityfunction$visitor),
            this.router.ridges().mapAll(densityfunction$visitor),
            settings.spawnTarget()
        );
    }

    public NormalNoise getOrCreateNoise(ResourceKey<NormalNoise.NoiseParameters> resourceKey) {
        return this.noiseIntances.computeIfAbsent(resourceKey, p_255589_ -> Noises.instantiate(this.noises, this.random, resourceKey));
    }

    public PositionalRandomFactory getOrCreateRandomFactory(ResourceLocation location) {
        return this.positionalRandoms.computeIfAbsent(location, p_224569_ -> this.random.fromHashOf(location).forkPositional());
    }

    public NoiseRouter router() {
        return this.router;
    }

    public Climate.Sampler sampler() {
        return this.sampler;
    }

    public SurfaceSystem surfaceSystem() {
        return this.surfaceSystem;
    }

    public PositionalRandomFactory aquiferRandom() {
        return this.aquiferRandom;
    }

    public PositionalRandomFactory oreRandom() {
        return this.oreRandom;
    }
}
