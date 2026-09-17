package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class StructureCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STRUCTURE = -1;
    private final ChunkScanAccess storageAccess;
    private final RegistryAccess registryAccess;
    private final StructureTemplateManager structureTemplateManager;
    private final ResourceKey<Level> dimension;
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final BiomeSource biomeSource;
    private final long seed;
    private final DataFixer fixerUpper;
    private final Long2ObjectMap<Object2IntMap<Structure>> loadedChunks = new Long2ObjectOpenHashMap<>();
    private final Map<Structure, Long2BooleanMap> featureChecks = new HashMap<>();

    public StructureCheck(
        ChunkScanAccess storageAccess,
        RegistryAccess registryAccess,
        StructureTemplateManager structureTemplateManager,
        ResourceKey<Level> dimension,
        ChunkGenerator chunkGenerator,
        RandomState randomState,
        LevelHeightAccessor heightAccessor,
        BiomeSource biomeSource,
        long seed,
        DataFixer fixerUpper
    ) {
        this.storageAccess = storageAccess;
        this.registryAccess = registryAccess;
        this.structureTemplateManager = structureTemplateManager;
        this.dimension = dimension;
        this.chunkGenerator = chunkGenerator;
        this.randomState = randomState;
        this.heightAccessor = heightAccessor;
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.fixerUpper = fixerUpper;
    }

    public StructureCheckResult checkStart(ChunkPos chunkPos, Structure structure, StructurePlacement placement, boolean skipKnownStructures) {
        long i = chunkPos.toLong();
        Object2IntMap<Structure> object2intmap = this.loadedChunks.get(i);
        if (object2intmap != null) {
            return this.checkStructureInfo(object2intmap, structure, skipKnownStructures);
        } else {
            StructureCheckResult structurecheckresult = this.tryLoadFromStorage(chunkPos, structure, skipKnownStructures, i);
            if (structurecheckresult != null) {
                return structurecheckresult;
            } else if (!placement.applyAdditionalChunkRestrictions(chunkPos.x, chunkPos.z, this.seed)) {
                return StructureCheckResult.START_NOT_PRESENT;
            } else {
                boolean flag = this.featureChecks
                    .computeIfAbsent(structure, p_226739_ -> new Long2BooleanOpenHashMap())
                    .computeIfAbsent(i, p_226728_ -> this.canCreateStructure(chunkPos, structure));
                return !flag ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED;
            }
        }
    }

    private boolean canCreateStructure(ChunkPos chunkPos, Structure structure) {
        return structure.findValidGenerationPoint(
                new Structure.GenerationContext(
                    this.registryAccess,
                    this.chunkGenerator,
                    this.biomeSource,
                    this.randomState,
                    this.structureTemplateManager,
                    this.seed,
                    chunkPos,
                    this.heightAccessor,
                    structure.biomes()::contains
                )
            )
            .isPresent();
    }

    @Nullable
    private StructureCheckResult tryLoadFromStorage(ChunkPos chunkPos, Structure structure, boolean skipKnownStructures, long packedChunkPos) {
        CollectFields collectfields = new CollectFields(
            new FieldSelector(IntTag.TYPE, "DataVersion"),
            new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"),
            new FieldSelector("structures", CompoundTag.TYPE, "starts")
        );

        try {
            this.storageAccess.scanChunk(chunkPos, collectfields).join();
        } catch (Exception exception1) {
            LOGGER.warn("Failed to read chunk {}", chunkPos, exception1);
            return StructureCheckResult.CHUNK_LOAD_NEEDED;
        }

        if (!(collectfields.getResult() instanceof CompoundTag compoundtag)) {
            return null;
        } else {
            int i = ChunkStorage.getVersion(compoundtag);
            if (i <= 1493) {
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            } else {
                ChunkStorage.injectDatafixingContext(compoundtag, this.dimension, this.chunkGenerator.getTypeNameForDataFixer());

                CompoundTag compoundtag1;
                try {
                    compoundtag1 = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, compoundtag, i);
                } catch (Exception exception) {
                    LOGGER.warn("Failed to partially datafix chunk {}", chunkPos, exception);
                    return StructureCheckResult.CHUNK_LOAD_NEEDED;
                }

                Object2IntMap<Structure> object2intmap = this.loadStructures(compoundtag1);
                if (object2intmap == null) {
                    return null;
                } else {
                    this.storeFullResults(packedChunkPos, object2intmap);
                    return this.checkStructureInfo(object2intmap, structure, skipKnownStructures);
                }
            }
        }
    }

    @Nullable
    private Object2IntMap<Structure> loadStructures(CompoundTag tag) {
        if (!tag.contains("structures", 10)) {
            return null;
        } else {
            CompoundTag compoundtag = tag.getCompound("structures");
            if (!compoundtag.contains("starts", 10)) {
                return null;
            } else {
                CompoundTag compoundtag1 = compoundtag.getCompound("starts");
                if (compoundtag1.isEmpty()) {
                    return Object2IntMaps.emptyMap();
                } else {
                    Object2IntMap<Structure> object2intmap = new Object2IntOpenHashMap<>();
                    Registry<Structure> registry = this.registryAccess.registryOrThrow(Registries.STRUCTURE);

                    for (String s : compoundtag1.getAllKeys()) {
                        ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
                        if (resourcelocation != null) {
                            Structure structure = registry.get(resourcelocation);
                            if (structure != null) {
                                CompoundTag compoundtag2 = compoundtag1.getCompound(s);
                                if (!compoundtag2.isEmpty()) {
                                    String s1 = compoundtag2.getString("id");
                                    if (!"INVALID".equals(s1)) {
                                        int i = compoundtag2.getInt("references");
                                        object2intmap.put(structure, i);
                                    }
                                }
                            }
                        }
                    }

                    return object2intmap;
                }
            }
        }
    }

    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> map) {
        return map.isEmpty() ? Object2IntMaps.emptyMap() : map;
    }

    private StructureCheckResult checkStructureInfo(Object2IntMap<Structure> structureChunks, Structure structure, boolean skipKnownStructures) {
        int i = structureChunks.getOrDefault(structure, -1);
        return i == -1 || skipKnownStructures && i != 0 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.START_PRESENT;
    }

    public void onStructureLoad(ChunkPos chunkPos, Map<Structure, StructureStart> chunkStarts) {
        long i = chunkPos.toLong();
        Object2IntMap<Structure> object2intmap = new Object2IntOpenHashMap<>();
        chunkStarts.forEach((p_226749_, p_226750_) -> {
            if (p_226750_.isValid()) {
                object2intmap.put(p_226749_, p_226750_.getReferences());
            }
        });
        this.storeFullResults(i, object2intmap);
    }

    private void storeFullResults(long chunkPos, Object2IntMap<Structure> structureChunks) {
        this.loadedChunks.put(chunkPos, deduplicateEmptyMap(structureChunks));
        this.featureChecks.values().forEach(p_209956_ -> p_209956_.remove(chunkPos));
    }

    public void incrementReference(ChunkPos pos, Structure structure) {
        this.loadedChunks.compute(pos.toLong(), (p_226745_, p_226746_) -> {
            if (p_226746_ == null || p_226746_.isEmpty()) {
                p_226746_ = new Object2IntOpenHashMap<>();
            }

            p_226746_.computeInt(structure, (p_226741_, p_226742_) -> p_226742_ == null ? 1 : p_226742_ + 1);
            return p_226746_;
        });
    }
}
