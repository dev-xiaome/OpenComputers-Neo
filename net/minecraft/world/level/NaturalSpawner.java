package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// TODO: ForgeHooks.canEntitySpawn
public final class NaturalSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_SPAWN_DISTANCE = 24;
    public static final int SPAWN_DISTANCE_CHUNK = 8;
    public static final int SPAWN_DISTANCE_BLOCK = 128;
    static final int MAGIC_NUMBER = (int)Math.pow(17.0, 2.0);
    private static final MobCategory[] SPAWNING_CATEGORIES = Stream.of(MobCategory.values())
        .filter(p_47037_ -> p_47037_ != MobCategory.MISC)
        .toArray(MobCategory[]::new);

    private NaturalSpawner() {
    }

    public static NaturalSpawner.SpawnState createState(
        int spawnableChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator
    ) {
        PotentialCalculator potentialcalculator = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> object2intopenhashmap = new Object2IntOpenHashMap<>();

        for (Entity entity : entities) {
            if (entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                continue;
            }

            MobCategory mobcategory = entity.getClassification(true);
            if (mobcategory != MobCategory.MISC) {
                BlockPos blockpos = entity.blockPosition();
                chunkGetter.query(
                    ChunkPos.asLong(blockpos),
                    p_275163_ -> {
                        MobSpawnSettings.MobSpawnCost mobspawnsettings$mobspawncost = getRoughBiome(blockpos, p_275163_)
                            .getMobSettings()
                            .getMobSpawnCost(entity.getType());
                        if (mobspawnsettings$mobspawncost != null) {
                            potentialcalculator.addCharge(entity.blockPosition(), mobspawnsettings$mobspawncost.charge());
                        }

                        if (entity instanceof Mob) {
                            calculator.addMob(p_275163_.getPos(), mobcategory);
                        }

                        object2intopenhashmap.addTo(mobcategory, 1);
                    }
                );
            }
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, object2intopenhashmap, potentialcalculator, calculator);
    }

    static Biome getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        return chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())).value();
    }

    public static void spawnForChunk(
        ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, boolean spawnFriendlies, boolean spawnMonsters, boolean forcedDespawn
    ) {
        level.getProfiler().push("spawner");

        for (MobCategory mobcategory : SPAWNING_CATEGORIES) {
            if ((spawnFriendlies || !mobcategory.isFriendly())
                && (spawnMonsters || mobcategory.isFriendly())
                && (forcedDespawn || !mobcategory.isPersistent())
                && spawnState.canSpawnForCategory(mobcategory, chunk.getPos())) {
                spawnCategoryForChunk(mobcategory, level, chunk, spawnState::canSpawn, spawnState::afterSpawn);
            }
        }

        level.getProfiler().pop();
    }

    public static void spawnCategoryForChunk(
        MobCategory category, ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnPredicate filter, NaturalSpawner.AfterSpawnCallback callback
    ) {
        BlockPos blockpos = getRandomPosWithin(level, chunk);
        if (blockpos.getY() >= level.getMinBuildHeight() + 1) {
            spawnCategoryForPosition(category, level, chunk, blockpos, filter, callback);
        }
    }

    @VisibleForDebug
    public static void spawnCategoryForPosition(MobCategory category, ServerLevel level, BlockPos pos) {
        spawnCategoryForPosition(
            category, level, level.getChunk(pos), pos, (p_151606_, p_151607_, p_151608_) -> true, (p_151610_, p_151611_) -> {
            }
        );
    }

    public static void spawnCategoryForPosition(
        MobCategory category,
        ServerLevel level,
        ChunkAccess chunk,
        BlockPos pos,
        NaturalSpawner.SpawnPredicate filter,
        NaturalSpawner.AfterSpawnCallback callback
    ) {
        StructureManager structuremanager = level.structureManager();
        ChunkGenerator chunkgenerator = level.getChunkSource().getGenerator();
        int i = pos.getY();
        BlockState blockstate = chunk.getBlockState(pos);
        if (!blockstate.isRedstoneConductor(chunk, pos)) {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            int j = 0;

            for (int k = 0; k < 3; k++) {
                int l = pos.getX();
                int i1 = pos.getZ();
                int j1 = 6;
                MobSpawnSettings.SpawnerData mobspawnsettings$spawnerdata = null;
                SpawnGroupData spawngroupdata = null;
                int k1 = Mth.ceil(level.random.nextFloat() * 4.0F);
                int l1 = 0;

                for (int i2 = 0; i2 < k1; i2++) {
                    l += level.random.nextInt(6) - level.random.nextInt(6);
                    i1 += level.random.nextInt(6) - level.random.nextInt(6);
                    blockpos$mutableblockpos.set(l, i, i1);
                    double d0 = (double)l + 0.5;
                    double d1 = (double)i1 + 0.5;
                    Player player = level.getNearestPlayer(d0, (double)i, d1, -1.0, false);
                    if (player != null) {
                        double d2 = player.distanceToSqr(d0, (double)i, d1);
                        if (isRightDistanceToPlayerAndSpawnPoint(level, chunk, blockpos$mutableblockpos, d2)) {
                            if (mobspawnsettings$spawnerdata == null) {
                                Optional<MobSpawnSettings.SpawnerData> optional = getRandomSpawnMobAt(
                                    level, structuremanager, chunkgenerator, category, level.random, blockpos$mutableblockpos
                                );
                                if (optional.isEmpty()) {
                                    break;
                                }

                                mobspawnsettings$spawnerdata = optional.get();
                                k1 = mobspawnsettings$spawnerdata.minCount
                                    + level.random.nextInt(1 + mobspawnsettings$spawnerdata.maxCount - mobspawnsettings$spawnerdata.minCount);
                            }

                            if (isValidSpawnPostitionForType(
                                    level, category, structuremanager, chunkgenerator, mobspawnsettings$spawnerdata, blockpos$mutableblockpos, d2
                                )
                                && filter.test(mobspawnsettings$spawnerdata.type, blockpos$mutableblockpos, chunk)) {
                                Mob mob = getMobForSpawn(level, mobspawnsettings$spawnerdata.type);
                                if (mob == null) {
                                    return;
                                }

                                mob.moveTo(d0, (double)i, d1, level.random.nextFloat() * 360.0F, 0.0F);
                                if (isValidPositionForMob(level, mob, d2)) {
                                    spawngroupdata = mob.finalizeSpawn(
                                        level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.NATURAL, spawngroupdata
                                    );
                                    j++;
                                    l1++;
                                    level.addFreshEntityWithPassengers(mob);
                                    callback.run(mob, chunk);
                                    if (j >= net.neoforged.neoforge.event.EventHooks.getMaxSpawnClusterSize(mob)) {
                                        return;
                                    }

                                    if (mob.isMaxGroupSizeReached(l1)) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isRightDistanceToPlayerAndSpawnPoint(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, double distance) {
        if (distance <= 576.0) {
            return false;
        } else {
            return level.getSharedSpawnPos()
                    .closerToCenterThan(new Vec3((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5), 24.0)
                ? false
                : Objects.equals(new ChunkPos(pos), chunk.getPos()) || level.isNaturalSpawningAllowed(pos);
        }
    }

    private static boolean isValidSpawnPostitionForType(
        ServerLevel level,
        MobCategory category,
        StructureManager structureManager,
        ChunkGenerator generator,
        MobSpawnSettings.SpawnerData data,
        BlockPos.MutableBlockPos pos,
        double distance
    ) {
        EntityType<?> entitytype = data.type;
        if (entitytype.getCategory() == MobCategory.MISC) {
            return false;
        } else if (!entitytype.canSpawnFarFromPlayer()
            && distance > (double)(entitytype.getCategory().getDespawnDistance() * entitytype.getCategory().getDespawnDistance())) {
            return false;
        } else if (!entitytype.canSummon() || !canSpawnMobAt(level, structureManager, generator, category, data, pos)) {
            return false;
        } else if (!SpawnPlacements.isSpawnPositionOk(entitytype, level, pos)) {
            return false;
        } else {
            return !SpawnPlacements.checkSpawnRules(entitytype, level, MobSpawnType.NATURAL, pos, level.random)
                ? false
                : level.noCollision(entitytype.getSpawnAABB((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5));
        }
    }

    @Nullable
    private static Mob getMobForSpawn(ServerLevel level, EntityType<?> entityType) {
        try {
            Entity entity = entityType.create(level);
            if (entity instanceof Mob) {
                return (Mob)entity;
            }

            LOGGER.warn("Can't spawn entity of type: {}", BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
        } catch (Exception exception) {
            LOGGER.warn("Failed to create mob", (Throwable)exception);
        }

        return null;
    }

    private static boolean isValidPositionForMob(ServerLevel level, Mob mob, double distance) {
        return distance > (double)(mob.getType().getCategory().getDespawnDistance() * mob.getType().getCategory().getDespawnDistance())
                && mob.removeWhenFarAway(distance)
            ? false
            : net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
    }

    private static Optional<MobSpawnSettings.SpawnerData> getRandomSpawnMobAt(
        ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory category, RandomSource random, BlockPos pos
    ) {
        Holder<Biome> holder = level.getBiome(pos);
        return category == MobCategory.WATER_AMBIENT && holder.is(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS) && random.nextFloat() < 0.98F
            ? Optional.empty()
            : mobsAt(level, structureManager, generator, category, pos, holder).getRandom(random);
    }

    private static boolean canSpawnMobAt(
        ServerLevel level,
        StructureManager structureManager,
        ChunkGenerator generator,
        MobCategory category,
        MobSpawnSettings.SpawnerData data,
        BlockPos pos
    ) {
        return mobsAt(level, structureManager, generator, category, pos, null).unwrap().contains(data);
    }

    private static WeightedRandomList<MobSpawnSettings.SpawnerData> mobsAt(
        ServerLevel level,
        StructureManager structureManager,
        ChunkGenerator generator,
        MobCategory category,
        BlockPos pos,
        @Nullable Holder<Biome> biome
    ) {
        // Forge: Add in potential spawns, and replace hardcoded nether fortress mob list
        if (isInNetherFortressBounds(pos, level, category, structureManager)) {
            var monsterSpawns = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.FORTRESS).spawnOverrides().get(MobCategory.MONSTER);
            if (monsterSpawns != null) { // structure modifiers can clear the spawn overrides
                return net.neoforged.neoforge.event.EventHooks.getPotentialSpawns(level, category, pos, monsterSpawns.spawns());
            }
        }
        return net.neoforged.neoforge.event.EventHooks.getPotentialSpawns(level, category, pos, generator.getMobsAt(biome != null ? biome : level.getBiome(pos), structureManager, category, pos));
    }

    public static boolean isInNetherFortressBounds(BlockPos pos, ServerLevel level, MobCategory category, StructureManager structureManager) {
        if (category == MobCategory.MONSTER && level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)) {
            Structure structure = structureManager.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.FORTRESS);
            return structure == null ? false : structureManager.getStructureAt(pos, structure).isValid();
        } else {
            return false;
        }
    }

    private static BlockPos getRandomPosWithin(Level level, LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX() + level.random.nextInt(16);
        int j = chunkpos.getMinBlockZ() + level.random.nextInt(16);
        int k = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, j) + 1;
        int l = Mth.randomBetweenInclusive(level.random, level.getMinBuildHeight(), k);
        return new BlockPos(i, l, j);
    }

    public static boolean isValidEmptySpawnBlock(BlockGetter block, BlockPos pos, BlockState blockState, FluidState fluidState, EntityType<?> entityType) {
        if (blockState.isCollisionShapeFullBlock(block, pos)) {
            return false;
        } else if (blockState.isSignalSource()) {
            return false;
        } else if (!fluidState.isEmpty()) {
            return false;
        } else {
            return blockState.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) ? false : !entityType.isBlockDangerous(blockState);
        }
    }

    public static void spawnMobsForChunkGeneration(ServerLevelAccessor levelAccessor, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random) {
        MobSpawnSettings mobspawnsettings = biome.value().getMobSettings();
        WeightedRandomList<MobSpawnSettings.SpawnerData> weightedrandomlist = mobspawnsettings.getMobs(MobCategory.CREATURE);
        if (!weightedrandomlist.isEmpty()) {
            int i = chunkPos.getMinBlockX();
            int j = chunkPos.getMinBlockZ();

            while (random.nextFloat() < mobspawnsettings.getCreatureProbability()) {
                Optional<MobSpawnSettings.SpawnerData> optional = weightedrandomlist.getRandom(random);
                if (!optional.isEmpty()) {
                    MobSpawnSettings.SpawnerData mobspawnsettings$spawnerdata = optional.get();
                    int k = mobspawnsettings$spawnerdata.minCount
                        + random.nextInt(1 + mobspawnsettings$spawnerdata.maxCount - mobspawnsettings$spawnerdata.minCount);
                    SpawnGroupData spawngroupdata = null;
                    int l = i + random.nextInt(16);
                    int i1 = j + random.nextInt(16);
                    int j1 = l;
                    int k1 = i1;

                    for (int l1 = 0; l1 < k; l1++) {
                        boolean flag = false;

                        for (int i2 = 0; !flag && i2 < 4; i2++) {
                            BlockPos blockpos = getTopNonCollidingPos(levelAccessor, mobspawnsettings$spawnerdata.type, l, i1);
                            if (mobspawnsettings$spawnerdata.type.canSummon()
                                && SpawnPlacements.isSpawnPositionOk(mobspawnsettings$spawnerdata.type, levelAccessor, blockpos)) {
                                float f = mobspawnsettings$spawnerdata.type.getWidth();
                                double d0 = Mth.clamp((double)l, (double)i + (double)f, (double)i + 16.0 - (double)f);
                                double d1 = Mth.clamp((double)i1, (double)j + (double)f, (double)j + 16.0 - (double)f);
                                if (!levelAccessor.noCollision(mobspawnsettings$spawnerdata.type.getSpawnAABB(d0, (double)blockpos.getY(), d1))
                                    || !SpawnPlacements.checkSpawnRules(
                                        mobspawnsettings$spawnerdata.type,
                                        levelAccessor,
                                        MobSpawnType.CHUNK_GENERATION,
                                        BlockPos.containing(d0, (double)blockpos.getY(), d1),
                                        levelAccessor.getRandom()
                                    )) {
                                    continue;
                                }

                                Entity entity;
                                try {
                                    entity = mobspawnsettings$spawnerdata.type.create(levelAccessor.getLevel());
                                } catch (Exception exception) {
                                    LOGGER.warn("Failed to create mob", (Throwable)exception);
                                    continue;
                                }

                                if (entity == null) {
                                    continue;
                                }

                                entity.moveTo(d0, (double)blockpos.getY(), d1, random.nextFloat() * 360.0F, 0.0F);
                                if (entity instanceof Mob mob
                                    && net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, levelAccessor, MobSpawnType.CHUNK_GENERATION)) {
                                    spawngroupdata = mob.finalizeSpawn(
                                        levelAccessor, levelAccessor.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.CHUNK_GENERATION, spawngroupdata
                                    );
                                    levelAccessor.addFreshEntityWithPassengers(mob);
                                    flag = true;
                                }
                            }

                            l += random.nextInt(5) - random.nextInt(5);

                            for (i1 += random.nextInt(5) - random.nextInt(5);
                                l < i || l >= i + 16 || i1 < j || i1 >= j + 16;
                                i1 = k1 + random.nextInt(5) - random.nextInt(5)
                            ) {
                                l = j1 + random.nextInt(5) - random.nextInt(5);
                            }
                        }
                    }
                }
            }
        }
    }

    private static BlockPos getTopNonCollidingPos(LevelReader level, EntityType<?> entityType, int x, int z) {
        int i = level.getHeight(SpawnPlacements.getHeightmapType(entityType), x, z);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(x, i, z);
        if (level.dimensionType().hasCeiling()) {
            do {
                blockpos$mutableblockpos.move(Direction.DOWN);
            } while (!level.getBlockState(blockpos$mutableblockpos).isAir());

            do {
                blockpos$mutableblockpos.move(Direction.DOWN);
            } while (level.getBlockState(blockpos$mutableblockpos).isAir() && blockpos$mutableblockpos.getY() > level.getMinBuildHeight());
        }

        return SpawnPlacements.getPlacementType(entityType).adjustSpawnPosition(level, blockpos$mutableblockpos.immutable());
    }

    @FunctionalInterface
    public interface AfterSpawnCallback {
        void run(Mob mob, ChunkAccess chunk);
    }

    @FunctionalInterface
    public interface ChunkGetter {
        void query(long chunkPos, Consumer<LevelChunk> chunkConsumer);
    }

    @FunctionalInterface
    public interface SpawnPredicate {
        boolean test(EntityType<?> entityType, BlockPos pos, ChunkAccess chunk);
    }

    public static class SpawnState {
        private final int spawnableChunkCount;
        private final Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
        private final PotentialCalculator spawnPotential;
        private final Object2IntMap<MobCategory> unmodifiableMobCategoryCounts;
        private final LocalMobCapCalculator localMobCapCalculator;
        @Nullable
        private BlockPos lastCheckedPos;
        @Nullable
        private EntityType<?> lastCheckedType;
        private double lastCharge;

        SpawnState(int spawnableChunkCount, Object2IntOpenHashMap<MobCategory> mobCategoryCounts, PotentialCalculator spawnPotential, LocalMobCapCalculator localMobCapCalculator) {
            this.spawnableChunkCount = spawnableChunkCount;
            this.mobCategoryCounts = mobCategoryCounts;
            this.spawnPotential = spawnPotential;
            this.localMobCapCalculator = localMobCapCalculator;
            this.unmodifiableMobCategoryCounts = Object2IntMaps.unmodifiable(mobCategoryCounts);
        }

        private boolean canSpawn(EntityType<?> entityType, BlockPos pos, ChunkAccess chunk) {
            this.lastCheckedPos = pos;
            this.lastCheckedType = entityType;
            MobSpawnSettings.MobSpawnCost mobspawnsettings$mobspawncost = NaturalSpawner.getRoughBiome(pos, chunk)
                .getMobSettings()
                .getMobSpawnCost(entityType);
            if (mobspawnsettings$mobspawncost == null) {
                this.lastCharge = 0.0;
                return true;
            } else {
                double d0 = mobspawnsettings$mobspawncost.charge();
                this.lastCharge = d0;
                double d1 = this.spawnPotential.getPotentialEnergyChange(pos, d0);
                return d1 <= mobspawnsettings$mobspawncost.energyBudget();
            }
        }

        private void afterSpawn(Mob mob, ChunkAccess chunk) {
            EntityType<?> entitytype = mob.getType();
            BlockPos blockpos = mob.blockPosition();
            double d0;
            if (blockpos.equals(this.lastCheckedPos) && entitytype == this.lastCheckedType) {
                d0 = this.lastCharge;
            } else {
                MobSpawnSettings.MobSpawnCost mobspawnsettings$mobspawncost = NaturalSpawner.getRoughBiome(blockpos, chunk)
                    .getMobSettings()
                    .getMobSpawnCost(entitytype);
                if (mobspawnsettings$mobspawncost != null) {
                    d0 = mobspawnsettings$mobspawncost.charge();
                } else {
                    d0 = 0.0;
                }
            }

            this.spawnPotential.addCharge(blockpos, d0);
            MobCategory mobcategory = entitytype.getCategory();
            this.mobCategoryCounts.addTo(mobcategory, 1);
            this.localMobCapCalculator.addMob(new ChunkPos(blockpos), mobcategory);
        }

        public int getSpawnableChunkCount() {
            return this.spawnableChunkCount;
        }

        public Object2IntMap<MobCategory> getMobCategoryCounts() {
            return this.unmodifiableMobCategoryCounts;
        }

        boolean canSpawnForCategory(MobCategory category, ChunkPos pos) {
            int i = category.getMaxInstancesPerChunk() * this.spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
            return this.mobCategoryCounts.getInt(category) >= i ? false : this.localMobCapCalculator.canSpawn(category, pos);
        }
    }
}
