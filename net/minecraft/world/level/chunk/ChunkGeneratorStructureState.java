package net.minecraft.world.level.chunk;

import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.slf4j.Logger;

public class ChunkGeneratorStructureState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RandomState randomState;
    private final BiomeSource biomeSource;
    private final long levelSeed;
    private final long concentricRingsSeed;
    private final Map<Structure, List<StructurePlacement>> placementsForStructure = new Object2ObjectOpenHashMap<>();
    private final Map<ConcentricRingsStructurePlacement, CompletableFuture<List<ChunkPos>>> ringPositions = new Object2ObjectArrayMap<>();
    private boolean hasGeneratedPositions;
    private final List<Holder<StructureSet>> possibleStructureSets;

    public static ChunkGeneratorStructureState createForFlat(
        RandomState randomState, long levelSeed, BiomeSource biomeSource, Stream<Holder<StructureSet>> structureSets
    ) {
        List<Holder<StructureSet>> list = structureSets.filter(p_255616_ -> hasBiomesForStructureSet(p_255616_.value(), biomeSource)).toList();
        return new ChunkGeneratorStructureState(randomState, biomeSource, levelSeed, 0L, list);
    }

    public static ChunkGeneratorStructureState createForNormal(
        RandomState randomState, long seed, BiomeSource biomeSource, HolderLookup<StructureSet> structureSetLookup
    ) {
        List<Holder<StructureSet>> list = structureSetLookup.listElements()
            .filter(p_256144_ -> hasBiomesForStructureSet(p_256144_.value(), biomeSource))
            .collect(Collectors.toUnmodifiableList());
        return new ChunkGeneratorStructureState(randomState, biomeSource, seed, seed, list);
    }

    private static boolean hasBiomesForStructureSet(StructureSet structureSet, BiomeSource biomeSource) {
        Stream<Holder<Biome>> stream = structureSet.structures().stream().flatMap(p_255738_ -> {
            Structure structure = p_255738_.structure().value();
            return structure.biomes().stream();
        });
        return stream.anyMatch(biomeSource.possibleBiomes()::contains);
    }

    private ChunkGeneratorStructureState(RandomState randomState, BiomeSource biomeSource, long levelSeed, long cocentricRingsSeed, List<Holder<StructureSet>> possibleStructureSets) {
        this.randomState = randomState;
        this.levelSeed = levelSeed;
        this.biomeSource = biomeSource;
        this.concentricRingsSeed = cocentricRingsSeed;
        this.possibleStructureSets = possibleStructureSets;
    }

    public List<Holder<StructureSet>> possibleStructureSets() {
        return this.possibleStructureSets;
    }

    private void generatePositions() {
        Set<Holder<Biome>> set = this.biomeSource.possibleBiomes();
        this.possibleStructureSets()
            .forEach(
                p_255638_ -> {
                    StructureSet structureset = p_255638_.value();
                    boolean flag = false;

                    for (StructureSet.StructureSelectionEntry structureset$structureselectionentry : structureset.structures()) {
                        Structure structure = structureset$structureselectionentry.structure().value();
                        if (structure.biomes().stream().anyMatch(set::contains)) {
                            this.placementsForStructure.computeIfAbsent(structure, p_256235_ -> new ArrayList<>()).add(structureset.placement());
                            flag = true;
                        }
                    }

                    if (flag && structureset.placement() instanceof ConcentricRingsStructurePlacement concentricringsstructureplacement) {
                        this.ringPositions
                            .put(
                                concentricringsstructureplacement,
                                this.generateRingPositions((Holder<StructureSet>)p_255638_, concentricringsstructureplacement)
                            );
                    }
                }
            );
    }

    private CompletableFuture<List<ChunkPos>> generateRingPositions(Holder<StructureSet> structureSet, ConcentricRingsStructurePlacement placement) {
        if (placement.count() == 0) {
            return CompletableFuture.completedFuture(List.of());
        } else {
            Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
            int i = placement.distance();
            int j = placement.count();
            List<CompletableFuture<ChunkPos>> list = new ArrayList<>(j);
            int k = placement.spread();
            HolderSet<Biome> holderset = placement.preferredBiomes();
            RandomSource randomsource = RandomSource.create();
            randomsource.setSeed(this.concentricRingsSeed);
            double d0 = randomsource.nextDouble() * Math.PI * 2.0;
            int l = 0;
            int i1 = 0;

            for (int j1 = 0; j1 < j; j1++) {
                double d1 = (double)(4 * i + i * i1 * 6) + (randomsource.nextDouble() - 0.5) * (double)i * 2.5;
                int k1 = (int)Math.round(Math.cos(d0) * d1);
                int l1 = (int)Math.round(Math.sin(d0) * d1);
                RandomSource randomsource1 = randomsource.fork();
                list.add(
                    CompletableFuture.supplyAsync(
                        () -> {
                            Pair<BlockPos, Holder<Biome>> pair = this.biomeSource
                                .findBiomeHorizontal(
                                    SectionPos.sectionToBlockCoord(k1, 8),
                                    0,
                                    SectionPos.sectionToBlockCoord(l1, 8),
                                    112,
                                    holderset::contains,
                                    randomsource1,
                                    this.randomState.sampler()
                                );
                            if (pair != null) {
                                BlockPos blockpos = pair.getFirst();
                                return new ChunkPos(SectionPos.blockToSectionCoord(blockpos.getX()), SectionPos.blockToSectionCoord(blockpos.getZ()));
                            } else {
                                return new ChunkPos(k1, l1);
                            }
                        },
                        Util.backgroundExecutor()
                    )
                );
                d0 += (Math.PI * 2) / (double)k;
                if (++l == k) {
                    i1++;
                    l = 0;
                    k += 2 * k / (i1 + 1);
                    k = Math.min(k, j - j1);
                    d0 += randomsource.nextDouble() * Math.PI * 2.0;
                }
            }

            return Util.sequence(list).thenApply(p_256372_ -> {
                double d2 = (double)stopwatch.stop().elapsed(TimeUnit.MILLISECONDS) / 1000.0;
                LOGGER.debug("Calculation for {} took {}s", structureSet, d2);
                return p_256372_;
            });
        }
    }

    public void ensureStructuresGenerated() {
        if (!this.hasGeneratedPositions) {
            this.generatePositions();
            this.hasGeneratedPositions = true;
        }
    }

    @Nullable
    public List<ChunkPos> getRingPositionsFor(ConcentricRingsStructurePlacement placement) {
        this.ensureStructuresGenerated();
        CompletableFuture<List<ChunkPos>> completablefuture = this.ringPositions.get(placement);
        return completablefuture != null ? completablefuture.join() : null;
    }

    public List<StructurePlacement> getPlacementsForStructure(Holder<Structure> structure) {
        this.ensureStructuresGenerated();
        return this.placementsForStructure.getOrDefault(structure.value(), List.of());
    }

    public RandomState randomState() {
        return this.randomState;
    }

    public boolean hasStructureChunkInRange(Holder<StructureSet> structureSet, int x, int z, int range) {
        StructurePlacement structureplacement = structureSet.value().placement();

        for (int i = x - range; i <= x + range; i++) {
            for (int j = z - range; j <= z + range; j++) {
                if (structureplacement.isStructureChunk(this, i, j)) {
                    return true;
                }
            }
        }

        return false;
    }

    public long getLevelSeed() {
        return this.levelSeed;
    }
}
