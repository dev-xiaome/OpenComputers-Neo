package net.minecraft.world.level.chunk;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class ChunkGenerator {
    public static final Codec<ChunkGenerator> CODEC = BuiltInRegistries.CHUNK_GENERATOR
        .byNameCodec()
        .dispatchStable(ChunkGenerator::codec, Function.identity());
    protected final BiomeSource biomeSource;
    private final Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;
    private final Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter;

    public ChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, p_223234_ -> p_223234_.value().getGenerationSettings());
    }

    public ChunkGenerator(BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter) {
        this.biomeSource = biomeSource;
        this.generationSettingsGetter = generationSettingsGetter;
        this.featuresPerStep = net.neoforged.neoforge.common.util.Lazy.of(
            () -> FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()), p_223216_ -> generationSettingsGetter.apply(p_223216_).features(), true)
        );
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void refreshFeaturesPerStep() {
        ((net.neoforged.neoforge.common.util.Lazy<?>) this.featuresPerStep).invalidate();
    }

    public void validate() {
        this.featuresPerStep.get();
    }

    protected abstract MapCodec<? extends ChunkGenerator> codec();

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForNormal(randomState, seed, this.biomeSource, structureSetLookup);
    }

    public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return BuiltInRegistries.CHUNK_GENERATOR.getResourceKey(this.codec());
    }

    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            chunk.fillBiomesFromNoise(this.biomeSource, randomState.sampler());
            return chunk;
        }), Util.backgroundExecutor());
    }

    public abstract void applyCarvers(
        WorldGenRegion level,
        long seed,
        RandomState random,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunk,
        GenerationStep.Carving step
    );

    @Nullable
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
        ServerLevel level, HolderSet<Structure> structure, BlockPos pos, int searchRadius, boolean skipKnownStructures
    ) {
        ChunkGeneratorStructureState chunkgeneratorstructurestate = level.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<Holder<Structure>>> map = new Object2ObjectArrayMap<>();

        for (Holder<Structure> holder : structure) {
            for (StructurePlacement structureplacement : chunkgeneratorstructurestate.getPlacementsForStructure(holder)) {
                map.computeIfAbsent(structureplacement, p_223127_ -> new ObjectArraySet<>()).add(holder);
            }
        }

        if (map.isEmpty()) {
            return null;
        } else {
            Pair<BlockPos, Holder<Structure>> pair2 = null;
            double d2 = Double.MAX_VALUE;
            StructureManager structuremanager = level.structureManager();
            List<Entry<StructurePlacement, Set<Holder<Structure>>>> list = new ArrayList<>(map.size());

            for (Entry<StructurePlacement, Set<Holder<Structure>>> entry : map.entrySet()) {
                StructurePlacement structureplacement1 = entry.getKey();
                if (structureplacement1 instanceof ConcentricRingsStructurePlacement) {
                    ConcentricRingsStructurePlacement concentricringsstructureplacement = (ConcentricRingsStructurePlacement)structureplacement1;
                    Pair<BlockPos, Holder<Structure>> pair = this.getNearestGeneratedStructure(
                        entry.getValue(), level, structuremanager, pos, skipKnownStructures, concentricringsstructureplacement
                    );
                    if (pair != null) {
                        BlockPos blockpos = pair.getFirst();
                        double d0 = pos.distSqr(blockpos);
                        if (d0 < d2) {
                            d2 = d0;
                            pair2 = pair;
                        }
                    }
                } else if (structureplacement1 instanceof RandomSpreadStructurePlacement) {
                    list.add(entry);
                }
            }

            if (!list.isEmpty()) {
                int i = SectionPos.blockToSectionCoord(pos.getX());
                int j = SectionPos.blockToSectionCoord(pos.getZ());

                for (int k = 0; k <= searchRadius; k++) {
                    boolean flag = false;

                    for (Entry<StructurePlacement, Set<Holder<Structure>>> entry1 : list) {
                        RandomSpreadStructurePlacement randomspreadstructureplacement = (RandomSpreadStructurePlacement)entry1.getKey();
                        Pair<BlockPos, Holder<Structure>> pair1 = getNearestGeneratedStructure(
                            entry1.getValue(),
                            level,
                            structuremanager,
                            i,
                            j,
                            k,
                            skipKnownStructures,
                            chunkgeneratorstructurestate.getLevelSeed(),
                            randomspreadstructureplacement
                        );
                        if (pair1 != null) {
                            flag = true;
                            double d1 = pos.distSqr(pair1.getFirst());
                            if (d1 < d2) {
                                d2 = d1;
                                pair2 = pair1;
                            }
                        }
                    }

                    if (flag) {
                        return pair2;
                    }
                }
            }

            return pair2;
        }
    }

    @Nullable
    private Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(
        Set<Holder<Structure>> structureHoldersSet,
        ServerLevel level,
        StructureManager structureManager,
        BlockPos pos,
        boolean skipKnownStructures,
        ConcentricRingsStructurePlacement placement
    ) {
        List<ChunkPos> list = level.getChunkSource().getGeneratorState().getRingPositionsFor(placement);
        if (list == null) {
            throw new IllegalStateException("Somehow tried to find structures for a placement that doesn't exist");
        } else {
            Pair<BlockPos, Holder<Structure>> pair = null;
            double d0 = Double.MAX_VALUE;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (ChunkPos chunkpos : list) {
                blockpos$mutableblockpos.set(SectionPos.sectionToBlockCoord(chunkpos.x, 8), 32, SectionPos.sectionToBlockCoord(chunkpos.z, 8));
                double d1 = blockpos$mutableblockpos.distSqr(pos);
                boolean flag = pair == null || d1 < d0;
                if (flag) {
                    Pair<BlockPos, Holder<Structure>> pair1 = getStructureGeneratingAt(structureHoldersSet, level, structureManager, skipKnownStructures, placement, chunkpos);
                    if (pair1 != null) {
                        pair = pair1;
                        d0 = d1;
                    }
                }
            }

            return pair;
        }
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(
        Set<Holder<Structure>> structureHoldersSet,
        LevelReader level,
        StructureManager structureManager,
        int x,
        int y,
        int z,
        boolean skipKnownStructures,
        long seed,
        RandomSpreadStructurePlacement spreadPlacement
    ) {
        int i = spreadPlacement.spacing();

        for (int j = -z; j <= z; j++) {
            boolean flag = j == -z || j == z;

            for (int k = -z; k <= z; k++) {
                boolean flag1 = k == -z || k == z;
                if (flag || flag1) {
                    int l = x + i * j;
                    int i1 = y + i * k;
                    ChunkPos chunkpos = spreadPlacement.getPotentialStructureChunk(seed, l, i1);
                    Pair<BlockPos, Holder<Structure>> pair = getStructureGeneratingAt(structureHoldersSet, level, structureManager, skipKnownStructures, spreadPlacement, chunkpos);
                    if (pair != null) {
                        return pair;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(
        Set<Holder<Structure>> structureHoldersSet,
        LevelReader level,
        StructureManager structureManager,
        boolean skipKnownStructures,
        StructurePlacement placement,
        ChunkPos chunkPos
    ) {
        for (Holder<Structure> holder : structureHoldersSet) {
            StructureCheckResult structurecheckresult = structureManager.checkStructurePresence(chunkPos, holder.value(), placement, skipKnownStructures);
            if (structurecheckresult != StructureCheckResult.START_NOT_PRESENT) {
                if (!skipKnownStructures && structurecheckresult == StructureCheckResult.START_PRESENT) {
                    return Pair.of(placement.getLocatePos(chunkPos), holder);
                }

                ChunkAccess chunkaccess = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
                StructureStart structurestart = structureManager.getStartForStructure(SectionPos.bottomOf(chunkaccess), holder.value(), chunkaccess);
                if (structurestart != null && structurestart.isValid() && (!skipKnownStructures || tryAddReference(structureManager, structurestart))) {
                    return Pair.of(placement.getLocatePos(structurestart.getChunkPos()), holder);
                }
            }
        }

        return null;
    }

    private static boolean tryAddReference(StructureManager structureManager, StructureStart structureStart) {
        if (structureStart.canBeReferenced()) {
            structureManager.addReference(structureStart);
            return true;
        } else {
            return false;
        }
    }

    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkpos = chunk.getPos();
        if (!SharedConstants.debugVoidTerrain(chunkpos)) {
            SectionPos sectionpos = SectionPos.of(chunkpos, level.getMinSection());
            BlockPos blockpos = sectionpos.origin();
            Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Map<Integer, List<Structure>> map = registry.stream().collect(Collectors.groupingBy(p_223103_ -> p_223103_.step().ordinal()));
            List<FeatureSorter.StepFeatureData> list = this.featuresPerStep.get();
            WorldgenRandom worldgenrandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            long i = worldgenrandom.setDecorationSeed(level.getSeed(), blockpos.getX(), blockpos.getZ());
            Set<Holder<Biome>> set = new ObjectArraySet<>();
            ChunkPos.rangeClosed(sectionpos.chunk(), 1).forEach(p_223093_ -> {
                ChunkAccess chunkaccess = level.getChunk(p_223093_.x, p_223093_.z);

                for (LevelChunkSection levelchunksection : chunkaccess.getSections()) {
                    levelchunksection.getBiomes().getAll(set::add);
                }
            });
            set.retainAll(this.biomeSource.possibleBiomes());
            int j = list.size();

            try {
                Registry<PlacedFeature> registry1 = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
                int i1 = Math.max(GenerationStep.Decoration.values().length, j);

                for (int k = 0; k < i1; k++) {
                    int l = 0;
                    if (structureManager.shouldGenerateStructures()) {
                        for (Structure structure : map.getOrDefault(k, Collections.emptyList())) {
                            worldgenrandom.setFeatureSeed(i, l, k);
                            Supplier<String> supplier = () -> registry.getResourceKey(structure).map(Object::toString).orElseGet(structure::toString);

                            try {
                                level.setCurrentlyGenerating(supplier);
                                structureManager.startsForStructure(sectionpos, structure)
                                    .forEach(
                                        p_223086_ -> p_223086_.placeInChunk(level, structureManager, this, worldgenrandom, getWritableArea(chunk), chunkpos)
                                    );
                            } catch (Exception exception) {
                                CrashReport crashreport1 = CrashReport.forThrowable(exception, "Feature placement");
                                crashreport1.addCategory("Feature").setDetail("Description", supplier::get);
                                throw new ReportedException(crashreport1);
                            }

                            l++;
                        }
                    }

                    if (k < j) {
                        IntSet intset = new IntArraySet();

                        for (Holder<Biome> holder : set) {
                            List<HolderSet<PlacedFeature>> list1 = this.generationSettingsGetter.apply(holder).features();
                            if (k < list1.size()) {
                                HolderSet<PlacedFeature> holderset = list1.get(k);
                                FeatureSorter.StepFeatureData featuresorter$stepfeaturedata1 = list.get(k);
                                holderset.stream()
                                    .map(Holder::value)
                                    .forEach(p_223174_ -> intset.add(featuresorter$stepfeaturedata1.indexMapping().applyAsInt(p_223174_)));
                            }
                        }

                        int j1 = intset.size();
                        int[] aint = intset.toIntArray();
                        Arrays.sort(aint);
                        FeatureSorter.StepFeatureData featuresorter$stepfeaturedata = list.get(k);

                        for (int k1 = 0; k1 < j1; k1++) {
                            int l1 = aint[k1];
                            PlacedFeature placedfeature = featuresorter$stepfeaturedata.features().get(l1);
                            Supplier<String> supplier1 = () -> registry1.getResourceKey(placedfeature).map(Object::toString).orElseGet(placedfeature::toString);
                            worldgenrandom.setFeatureSeed(i, l1, k);

                            try {
                                level.setCurrentlyGenerating(supplier1);
                                placedfeature.placeWithBiomeCheck(level, this, worldgenrandom, blockpos);
                            } catch (Exception exception1) {
                                CrashReport crashreport2 = CrashReport.forThrowable(exception1, "Feature placement");
                                crashreport2.addCategory("Feature").setDetail("Description", supplier1::get);
                                throw new ReportedException(crashreport2);
                            }
                        }
                    }
                }

                level.setCurrentlyGenerating(null);
            } catch (Exception exception2) {
                CrashReport crashreport = CrashReport.forThrowable(exception2, "Biome decoration");
                crashreport.addCategory("Generation").setDetail("CenterX", chunkpos.x).setDetail("CenterZ", chunkpos.z).setDetail("Decoration Seed", i);
                throw new ReportedException(crashreport);
            }
        }
    }

    private static BoundingBox getWritableArea(ChunkAccess chunk) {
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        LevelHeightAccessor levelheightaccessor = chunk.getHeightAccessorForGeneration();
        int k = levelheightaccessor.getMinBuildHeight() + 1;
        int l = levelheightaccessor.getMaxBuildHeight() - 1;
        return new BoundingBox(i, k, j, i + 15, l, j + 15);
    }

    public abstract void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk);

    public abstract void spawnOriginalMobs(WorldGenRegion level);

    public int getSpawnHeight(LevelHeightAccessor level) {
        return 64;
    }

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public abstract int getGenDepth();

    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(
        Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos
    ) {
        Map<Structure, LongSet> map = structureManager.getAllStructuresAt(pos);

        for (Entry<Structure, LongSet> entry : map.entrySet()) {
            Structure structure = entry.getKey();
            StructureSpawnOverride structurespawnoverride = structure.spawnOverrides().get(category);
            if (structurespawnoverride != null) {
                MutableBoolean mutableboolean = new MutableBoolean(false);
                Predicate<StructureStart> predicate = structurespawnoverride.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE
                    ? p_223065_ -> structureManager.structureHasPieceAt(pos, p_223065_)
                    : p_223130_ -> p_223130_.getBoundingBox().isInside(pos);
                structureManager.fillStartsForStructure(structure, entry.getValue(), p_223220_ -> {
                    if (mutableboolean.isFalse() && predicate.test(p_223220_)) {
                        mutableboolean.setTrue();
                    }
                });
                if (mutableboolean.isTrue()) {
                    return structurespawnoverride.spawns();
                }
            }
        }

        return biome.value().getMobSettings().getMobs(category);
    }

    public void createStructures(
        RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager structureTemplateManager
    ) {
        ChunkPos chunkpos = chunk.getPos();
        SectionPos sectionpos = SectionPos.bottomOf(chunk);
        RandomState randomstate = structureState.randomState();
        structureState.possibleStructureSets()
            .forEach(
                p_255564_ -> {
                    StructurePlacement structureplacement = p_255564_.value().placement();
                    List<StructureSet.StructureSelectionEntry> list = p_255564_.value().structures();

                    for (StructureSet.StructureSelectionEntry structureset$structureselectionentry : list) {
                        StructureStart structurestart = structureManager.getStartForStructure(
                            sectionpos, structureset$structureselectionentry.structure().value(), chunk
                        );
                        if (structurestart != null && structurestart.isValid()) {
                            return;
                        }
                    }

                    if (structureplacement.isStructureChunk(structureState, chunkpos.x, chunkpos.z)) {
                        if (list.size() == 1) {
                            this.tryGenerateStructure(
                                list.get(0), structureManager, registryAccess, randomstate, structureTemplateManager, structureState.getLevelSeed(), chunk, chunkpos, sectionpos
                            );
                        } else {
                            ArrayList<StructureSet.StructureSelectionEntry> arraylist = new ArrayList<>(list.size());
                            arraylist.addAll(list);
                            WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
                            worldgenrandom.setLargeFeatureSeed(structureState.getLevelSeed(), chunkpos.x, chunkpos.z);
                            int i = 0;

                            for (StructureSet.StructureSelectionEntry structureset$structureselectionentry1 : arraylist) {
                                i += structureset$structureselectionentry1.weight();
                            }

                            while (!arraylist.isEmpty()) {
                                int j = worldgenrandom.nextInt(i);
                                int k = 0;

                                for (StructureSet.StructureSelectionEntry structureset$structureselectionentry2 : arraylist) {
                                    j -= structureset$structureselectionentry2.weight();
                                    if (j < 0) {
                                        break;
                                    }

                                    k++;
                                }

                                StructureSet.StructureSelectionEntry structureset$structureselectionentry3 = arraylist.get(k);
                                if (this.tryGenerateStructure(
                                    structureset$structureselectionentry3,
                                    structureManager,
                                    registryAccess,
                                    randomstate,
                                    structureTemplateManager,
                                    structureState.getLevelSeed(),
                                    chunk,
                                    chunkpos,
                                    sectionpos
                                )) {
                                    return;
                                }

                                arraylist.remove(k);
                                i -= structureset$structureselectionentry3.weight();
                            }
                        }
                    }
                }
            );
    }

    private boolean tryGenerateStructure(
        StructureSet.StructureSelectionEntry structureSelectionEntry,
        StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState random,
        StructureTemplateManager structureTemplateManager,
        long seed,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        SectionPos sectionPos
    ) {
        Structure structure = structureSelectionEntry.structure().value();
        int i = fetchReferences(structureManager, chunk, sectionPos, structure);
        HolderSet<Biome> holderset = structure.biomes();
        Predicate<Holder<Biome>> predicate = holderset::contains;
        StructureStart structurestart = structure.generate(
            registryAccess, this, this.biomeSource, random, structureTemplateManager, seed, chunkPos, i, chunk, predicate
        );
        if (structurestart.isValid()) {
            structureManager.setStartForStructure(sectionPos, structure, structurestart, chunk);
            return true;
        } else {
            return false;
        }
    }

    private static int fetchReferences(StructureManager structureManager, ChunkAccess chunk, SectionPos sectionPos, Structure structure) {
        StructureStart structurestart = structureManager.getStartForStructure(sectionPos, structure, chunk);
        return structurestart != null ? structurestart.getReferences() : 0;
    }

    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        int i = 8;
        ChunkPos chunkpos = chunk.getPos();
        int j = chunkpos.x;
        int k = chunkpos.z;
        int l = chunkpos.getMinBlockX();
        int i1 = chunkpos.getMinBlockZ();
        SectionPos sectionpos = SectionPos.bottomOf(chunk);

        for (int j1 = j - 8; j1 <= j + 8; j1++) {
            for (int k1 = k - 8; k1 <= k + 8; k1++) {
                long l1 = ChunkPos.asLong(j1, k1);

                for (StructureStart structurestart : level.getChunk(j1, k1).getAllStarts().values()) {
                    try {
                        if (structurestart.isValid() && structurestart.getBoundingBox().intersects(l, i1, l + 15, i1 + 15)) {
                            structureManager.addReferenceForStructure(sectionpos, structurestart.getStructure(), l1, chunk);
                            DebugPackets.sendStructurePacket(level, structurestart);
                        }
                    } catch (Exception exception) {
                        CrashReport crashreport = CrashReport.forThrowable(exception, "Generating structure reference");
                        CrashReportCategory crashreportcategory = crashreport.addCategory("Structure");
                        Optional<? extends Registry<Structure>> optional = level.registryAccess().registry(Registries.STRUCTURE);
                        crashreportcategory.setDetail(
                            "Id", () -> optional.<String>map(p_258977_ -> p_258977_.getKey(structurestart.getStructure()).toString()).orElse("UNKNOWN")
                        );
                        crashreportcategory.setDetail("Name", () -> BuiltInRegistries.STRUCTURE_TYPE.getKey(structurestart.getStructure().type()).toString());
                        crashreportcategory.setDetail("Class", () -> structurestart.getStructure().getClass().getCanonicalName());
                        throw new ReportedException(crashreport);
                    }
                }
            }
        }
    }

    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk);

    public abstract int getSeaLevel();

    public abstract int getMinY();

    public abstract int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random);

    public abstract NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random);

    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return this.getBaseHeight(x, z, type, level, random);
    }

    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types types, LevelHeightAccessor level, RandomState random) {
        return this.getBaseHeight(x, z, types, level, random) - 1;
    }

    public abstract void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos);

    @Deprecated
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> biome) {
        return this.generationSettingsGetter.apply(biome);
    }
}
