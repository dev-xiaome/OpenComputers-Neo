package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public abstract class Structure {
    public static final Codec<Structure> DIRECT_CODEC = BuiltInRegistries.STRUCTURE_TYPE.byNameCodec().dispatch(Structure::type, StructureType::codec);
    public static final Codec<Holder<Structure>> CODEC = RegistryFileCodec.create(Registries.STRUCTURE, DIRECT_CODEC);
    /** Neo: Field accesses are redirected to {@link #getModifiedStructureSettings()} with a coremod. */
    private final Structure.StructureSettings settings;

    public static <S extends Structure> RecordCodecBuilder<S, Structure.StructureSettings> settingsCodec(Instance<S> instance) {
        return Structure.StructureSettings.CODEC.forGetter(p_226595_ -> p_226595_.modifiableStructureInfo().getOriginalStructureInfo().structureSettings()); // FORGE: Patch codec to ignore field redirect coremods.
    }

    public static <S extends Structure> MapCodec<S> simpleCodec(Function<Structure.StructureSettings, S> factory) {
        return RecordCodecBuilder.mapCodec(p_226611_ -> p_226611_.group(settingsCodec(p_226611_)).apply(p_226611_, factory));
    }

    protected Structure(Structure.StructureSettings settings) {
        this.settings = settings;
        this.modifiableStructureInfo = new net.neoforged.neoforge.common.world.ModifiableStructureInfo(new net.neoforged.neoforge.common.world.ModifiableStructureInfo.StructureInfo(settings)); // FORGE: cache original structure info on construction so we can bypass our field read coremods where necessary
    }

    public HolderSet<Biome> biomes() {
        return this.settings.biomes;
    }

    public Map<MobCategory, StructureSpawnOverride> spawnOverrides() {
        return this.settings.spawnOverrides;
    }

    public GenerationStep.Decoration step() {
        return this.settings.step;
    }

    public TerrainAdjustment terrainAdaptation() {
        return this.settings.terrainAdaptation;
    }

    public BoundingBox adjustBoundingBox(BoundingBox boundingBox) {
        return this.terrainAdaptation() != TerrainAdjustment.NONE ? boundingBox.inflatedBy(12) : boundingBox;
    }

    public StructureStart generate(
        RegistryAccess registryAccess,
        ChunkGenerator chunkGenerator,
        BiomeSource biomeSource,
        RandomState randomState,
        StructureTemplateManager structureTemplateManager,
        long seed,
        ChunkPos chunkPos,
        int references,
        LevelHeightAccessor heightAccessor,
        Predicate<Holder<Biome>> validBiome
    ) {
        Structure.GenerationContext structure$generationcontext = new Structure.GenerationContext(
            registryAccess, chunkGenerator, biomeSource, randomState, structureTemplateManager, seed, chunkPos, heightAccessor, validBiome
        );
        Optional<Structure.GenerationStub> optional = this.findValidGenerationPoint(structure$generationcontext);
        if (optional.isPresent()) {
            StructurePiecesBuilder structurepiecesbuilder = optional.get().getPiecesBuilder();
            StructureStart structurestart = new StructureStart(this, chunkPos, references, structurepiecesbuilder.build());
            if (structurestart.isValid()) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    protected static Optional<Structure.GenerationStub> onTopOfChunkCenter(
        Structure.GenerationContext context, Heightmap.Types heightmapTypes, Consumer<StructurePiecesBuilder> generator
    ) {
        ChunkPos chunkpos = context.chunkPos();
        int i = chunkpos.getMiddleBlockX();
        int j = chunkpos.getMiddleBlockZ();
        int k = context.chunkGenerator().getFirstOccupiedHeight(i, j, heightmapTypes, context.heightAccessor(), context.randomState());
        return Optional.of(new Structure.GenerationStub(new BlockPos(i, k, j), generator));
    }

    private static boolean isValidBiome(Structure.GenerationStub stub, Structure.GenerationContext context) {
        BlockPos blockpos = stub.position();
        return context.validBiome
            .test(
                context.chunkGenerator
                    .getBiomeSource()
                    .getNoiseBiome(
                        QuartPos.fromBlock(blockpos.getX()),
                        QuartPos.fromBlock(blockpos.getY()),
                        QuartPos.fromBlock(blockpos.getZ()),
                        context.randomState.sampler()
                    )
            );
    }

    public void afterPlace(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BoundingBox boundingBox,
        ChunkPos chunkPos,
        PiecesContainer pieces
    ) {
    }

    private static int[] getCornerHeights(Structure.GenerationContext context, int minX, int maxX, int minZ, int maxZ) {
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        LevelHeightAccessor levelheightaccessor = context.heightAccessor();
        RandomState randomstate = context.randomState();
        return new int[]{
            chunkgenerator.getFirstOccupiedHeight(minX, minZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate),
            chunkgenerator.getFirstOccupiedHeight(minX, minZ + maxZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate),
            chunkgenerator.getFirstOccupiedHeight(minX + maxX, minZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate),
            chunkgenerator.getFirstOccupiedHeight(
                minX + maxX, minZ + maxZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate
            )
        };
    }

    public static int getMeanFirstOccupiedHeight(Structure.GenerationContext context, int minX, int maxX, int minZ, int maxZ) {
        int[] aint = getCornerHeights(context, minX, maxX, minZ, maxZ);
        return (aint[0] + aint[1] + aint[2] + aint[3]) / 4;
    }

    protected static int getLowestY(Structure.GenerationContext context, int maxX, int maxZ) {
        ChunkPos chunkpos = context.chunkPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        return getLowestY(context, i, j, maxX, maxZ);
    }

    protected static int getLowestY(Structure.GenerationContext context, int minX, int minZ, int maxX, int maxZ) {
        int[] aint = getCornerHeights(context, minX, maxX, minZ, maxZ);
        return Math.min(Math.min(aint[0], aint[1]), Math.min(aint[2], aint[3]));
    }

    @Deprecated
    protected BlockPos getLowestYIn5by5BoxOffset7Blocks(Structure.GenerationContext context, Rotation rotation) {
        int i = 5;
        int j = 5;
        if (rotation == Rotation.CLOCKWISE_90) {
            i = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            i = -5;
            j = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            j = -5;
        }

        ChunkPos chunkpos = context.chunkPos();
        int k = chunkpos.getBlockX(7);
        int l = chunkpos.getBlockZ(7);
        return new BlockPos(k, getLowestY(context, k, l, i, j), l);
    }

    protected abstract Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context);

    public Optional<Structure.GenerationStub> findValidGenerationPoint(Structure.GenerationContext context) {
        return this.findGenerationPoint(context).filter(p_262911_ -> isValidBiome(p_262911_, context));
    }

    public abstract StructureType<?> type();

    // Neo: Grant ability to modify Structures in specific ways such as adding new natural mob spawns to structures
    private final net.neoforged.neoforge.common.world.ModifiableStructureInfo modifiableStructureInfo;

    /**
     * {@return Cache of original structure data and structure data modified by structure modifiers}
     * Modified structure data is set by server after datapacks and serverconfigs load.
     * Settings field reads are coremodded to redirect to this.
     **/
    public net.neoforged.neoforge.common.world.ModifiableStructureInfo modifiableStructureInfo() {
        return this.modifiableStructureInfo;
    }

    /**
     * {@return The structure's settings, with modifications if called after modifiers are applied in server init.}
     */
    public StructureSettings getModifiedStructureSettings() {
        return this.modifiableStructureInfo().get().structureSettings();
    }

    public static record GenerationContext(
        RegistryAccess registryAccess,
        ChunkGenerator chunkGenerator,
        BiomeSource biomeSource,
        RandomState randomState,
        StructureTemplateManager structureTemplateManager,
        WorldgenRandom random,
        long seed,
        ChunkPos chunkPos,
        LevelHeightAccessor heightAccessor,
        Predicate<Holder<Biome>> validBiome
    ) {
        public GenerationContext(
            RegistryAccess p_226632_,
            ChunkGenerator p_226633_,
            BiomeSource p_226634_,
            RandomState p_226635_,
            StructureTemplateManager p_226636_,
            long p_226637_,
            ChunkPos p_226638_,
            LevelHeightAccessor p_226639_,
            Predicate<Holder<Biome>> p_226640_
        ) {
            this(p_226632_, p_226633_, p_226634_, p_226635_, p_226636_, makeRandom(p_226637_, p_226638_), p_226637_, p_226638_, p_226639_, p_226640_);
        }

        private static WorldgenRandom makeRandom(long seed, ChunkPos chunkPos) {
            WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
            worldgenrandom.setLargeFeatureSeed(seed, chunkPos.x, chunkPos.z);
            return worldgenrandom;
        }
    }

    public static record GenerationStub(BlockPos position, Either<Consumer<StructurePiecesBuilder>, StructurePiecesBuilder> generator) {
        public GenerationStub(BlockPos p_226675_, Consumer<StructurePiecesBuilder> p_226676_) {
            this(p_226675_, Either.left(p_226676_));
        }

        public StructurePiecesBuilder getPiecesBuilder() {
            return this.generator.map(p_226681_ -> {
                StructurePiecesBuilder structurepiecesbuilder = new StructurePiecesBuilder();
                p_226681_.accept(structurepiecesbuilder);
                return structurepiecesbuilder;
            }, p_226679_ -> (StructurePiecesBuilder)p_226679_);
        }
    }

    public static record StructureSettings(
        HolderSet<Biome> biomes, Map<MobCategory, StructureSpawnOverride> spawnOverrides, GenerationStep.Decoration step, TerrainAdjustment terrainAdaptation
    ) {
        static final Structure.StructureSettings DEFAULT = new Structure.StructureSettings(
            HolderSet.direct(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE
        );
        public static final MapCodec<Structure.StructureSettings> CODEC = RecordCodecBuilder.mapCodec(
            p_351995_ -> p_351995_.group(
                        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(Structure.StructureSettings::biomes),
                        Codec.simpleMap(MobCategory.CODEC, StructureSpawnOverride.CODEC, StringRepresentable.keys(MobCategory.values()))
                            .fieldOf("spawn_overrides")
                            .forGetter(Structure.StructureSettings::spawnOverrides),
                        GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(Structure.StructureSettings::step),
                        TerrainAdjustment.CODEC
                            .optionalFieldOf("terrain_adaptation", DEFAULT.terrainAdaptation)
                            .forGetter(Structure.StructureSettings::terrainAdaptation)
                    )
                    .apply(p_351995_, Structure.StructureSettings::new)
        );

        public StructureSettings(HolderSet<Biome> p_352235_) {
            this(p_352235_, DEFAULT.spawnOverrides, DEFAULT.step, DEFAULT.terrainAdaptation);
        }

        public static class Builder {
            private final HolderSet<Biome> biomes;
            private Map<MobCategory, StructureSpawnOverride> spawnOverrides;
            private GenerationStep.Decoration step;
            private TerrainAdjustment terrainAdaption;

            public Builder(HolderSet<Biome> biomes) {
                this.spawnOverrides = Structure.StructureSettings.DEFAULT.spawnOverrides;
                this.step = Structure.StructureSettings.DEFAULT.step;
                this.terrainAdaption = Structure.StructureSettings.DEFAULT.terrainAdaptation;
                this.biomes = biomes;
            }

            public Structure.StructureSettings.Builder spawnOverrides(Map<MobCategory, StructureSpawnOverride> spawnOverrides) {
                this.spawnOverrides = spawnOverrides;
                return this;
            }

            public Structure.StructureSettings.Builder generationStep(GenerationStep.Decoration generationStep) {
                this.step = generationStep;
                return this;
            }

            public Structure.StructureSettings.Builder terrainAdapation(TerrainAdjustment terrainAdaptation) {
                this.terrainAdaption = terrainAdaptation;
                return this;
            }

            public Structure.StructureSettings build() {
                return new Structure.StructureSettings(this.biomes, this.spawnOverrides, this.step, this.terrainAdaption);
            }
        }
    }
}
