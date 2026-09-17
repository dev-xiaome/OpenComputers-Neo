package net.minecraft.client.multiplayer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientLevel extends Level {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double FLUID_PARTICLE_SPAWN_OFFSET = 0.05;
    private static final int NORMAL_LIGHT_UPDATES_PER_FRAME = 10;
    private static final int LIGHT_UPDATE_QUEUE_SIZE_THRESHOLD = 1000;
    final EntityTickList tickingEntities = new EntityTickList();
    private final TransientEntitySectionManager<Entity> entityStorage = new TransientEntitySectionManager<>(Entity.class, new ClientLevel.EntityCallbacks());
    private final ClientPacketListener connection;
    private final LevelRenderer levelRenderer;
    private final ClientLevel.ClientLevelData clientLevelData;
    private final DimensionSpecialEffects effects;
    private final TickRateManager tickRateManager;
    private final Minecraft minecraft = Minecraft.getInstance();
    final List<AbstractClientPlayer> players = Lists.newArrayList();
    private final Map<MapId, MapItemSavedData> mapData = Maps.newHashMap();
    private static final long CLOUD_COLOR = 16777215L;
    private int skyFlashTime;
    private final Object2ObjectArrayMap<ColorResolver, BlockTintCache> tintCaches = Util.make(
        new Object2ObjectArrayMap<>(3),
        p_194170_ -> {
            p_194170_.put(
                BiomeColors.GRASS_COLOR_RESOLVER, new BlockTintCache(p_194181_ -> this.calculateBlockTint(p_194181_, BiomeColors.GRASS_COLOR_RESOLVER))
            );
            p_194170_.put(
                BiomeColors.FOLIAGE_COLOR_RESOLVER, new BlockTintCache(p_194177_ -> this.calculateBlockTint(p_194177_, BiomeColors.FOLIAGE_COLOR_RESOLVER))
            );
            p_194170_.put(
                BiomeColors.WATER_COLOR_RESOLVER, new BlockTintCache(p_194168_ -> this.calculateBlockTint(p_194168_, BiomeColors.WATER_COLOR_RESOLVER))
            );
            net.neoforged.neoforge.client.ColorResolverManager.registerBlockTintCaches(ClientLevel.this, p_194170_);
        }
    );
    private final ClientChunkCache chunkSource;
    private final Deque<Runnable> lightUpdateQueue = Queues.newArrayDeque();
    private int serverSimulationDistance;
    private final BlockStatePredictionHandler blockStatePredictionHandler = new BlockStatePredictionHandler();
    private static final Set<Item> MARKER_PARTICLE_ITEMS = Set.of(Items.BARRIER, Items.LIGHT);
    private final it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.neoforged.neoforge.entity.PartEntity<?>> partEntities = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private final net.neoforged.neoforge.client.model.data.ModelDataManager modelDataManager = new net.neoforged.neoforge.client.model.data.ModelDataManager(this);

    public void handleBlockChangedAck(int sequence) {
        this.blockStatePredictionHandler.endPredictionsUpTo(sequence, this);
    }

    public void setServerVerifiedBlockState(BlockPos pos, BlockState state, int flags) {
        if (!this.blockStatePredictionHandler.updateKnownServerState(pos, state)) {
            super.setBlock(pos, state, flags, 512);
        }
    }

    public void syncBlockState(BlockPos pos, BlockState state, Vec3 playerPos) {
        BlockState blockstate = this.getBlockState(pos);
        if (blockstate != state) {
            this.setBlock(pos, state, 19);
            Player player = this.minecraft.player;
            if (this == player.level() && player.isColliding(pos, state)) {
                player.absMoveTo(playerPos.x, playerPos.y, playerPos.z);
            }
        }
    }

    BlockStatePredictionHandler getBlockStatePredictionHandler() {
        return this.blockStatePredictionHandler;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (this.blockStatePredictionHandler.isPredicting()) {
            // Neo: Record and store a snapshot in the prediction so that BE data can be restored if the break is denied.
            // Fixes MC-36093 and permits correct server-side only cancellation of block changes.
            var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(this.dimension(), this, pos, flags);

            BlockState blockstate = this.getBlockState(pos);
            boolean flag = super.setBlock(pos, state, flags, recursionLeft);
            if (flag) {
                this.blockStatePredictionHandler.retainKnownServerState(pos, blockstate, this.minecraft.player);
                this.blockStatePredictionHandler.retainSnapshot(pos, snapshot);
            }

            return flag;
        } else {
            return super.setBlock(pos, state, flags, recursionLeft);
        }
    }

    public ClientLevel(
        ClientPacketListener connection,
        ClientLevel.ClientLevelData clientLevelData,
        ResourceKey<Level> dimension,
        Holder<DimensionType> dimensionType,
        int viewDistance,
        int serverSimulationDistance,
        Supplier<ProfilerFiller> profiler,
        LevelRenderer levelRenderer,
        boolean isDebug,
        long biomeZoomSeed
    ) {
        super(clientLevelData, dimension, connection.registryAccess(), dimensionType, profiler, true, isDebug, biomeZoomSeed, 1000000);
        this.connection = connection;
        this.chunkSource = new ClientChunkCache(this, viewDistance);
        this.tickRateManager = new TickRateManager();
        this.clientLevelData = clientLevelData;
        this.levelRenderer = levelRenderer;
        this.effects = DimensionSpecialEffects.forType(dimensionType.value());
        this.setDefaultSpawnPos(new BlockPos(8, 64, 8), 0.0F);
        this.serverSimulationDistance = serverSimulationDistance;
        this.updateSkyBrightness();
        this.prepareWeather();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Load(this));
    }

    public void queueLightUpdate(Runnable task) {
        this.lightUpdateQueue.add(task);
    }

    public void pollLightUpdates() {
        int i = this.lightUpdateQueue.size();
        int j = i < 1000 ? Math.max(10, i / 10) : i;

        for (int k = 0; k < j; k++) {
            Runnable runnable = this.lightUpdateQueue.poll();
            if (runnable == null) {
                break;
            }

            runnable.run();
        }
    }

    public boolean isLightUpdateQueueEmpty() {
        return this.lightUpdateQueue.isEmpty();
    }

    public DimensionSpecialEffects effects() {
        return this.effects;
    }

    /**
     * Runs a single tick for the world
     */
    public void tick(BooleanSupplier hasTimeLeft) {
        this.getWorldBorder().tick();
        if (this.tickRateManager().runsNormally()) {
            this.tickTime();
        }

        if (this.skyFlashTime > 0) {
            this.setSkyFlashTime(this.skyFlashTime - 1);
        }

        this.getProfiler().push("blocks");
        this.chunkSource.tick(hasTimeLeft, true);
        this.getProfiler().pop();
    }

    private void tickTime() {
        this.setGameTime(this.levelData.getGameTime() + 1L);
        if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            this.setDayTime(this.levelData.getDayTime() + advanceDaytime());
        }
    }

    public void setGameTime(long time) {
        this.clientLevelData.setGameTime(time);
    }

    /**
     * Sets the world time.
     */
    public void setDayTime(long time) {
        if (time < 0L) {
            time = -time;
            this.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        } else {
            this.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, null);
        }

        this.clientLevelData.setDayTime(time);
    }

    public Iterable<Entity> entitiesForRendering() {
        return this.getEntities().getAll();
    }

    public void tickEntities() {
        ProfilerFiller profilerfiller = this.getProfiler();
        profilerfiller.push("entities");
        this.tickingEntities.forEach(p_308457_ -> {
            if (!p_308457_.isRemoved() && !p_308457_.isPassenger() && !this.tickRateManager.isEntityFrozen(p_308457_)) {
                this.guardEntityTick(this::tickNonPassenger, p_308457_);
            }
        });
        profilerfiller.pop();
        this.tickBlockEntities();
    }

    @Override
    public boolean shouldTickDeath(Entity entity) {
        return entity.chunkPosition().getChessboardDistance(this.minecraft.player.chunkPosition()) <= this.serverSimulationDistance;
    }

    public void tickNonPassenger(Entity p_entity) {
        p_entity.setOldPosAndRot();
        p_entity.tickCount++;
        this.getProfiler().push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(p_entity.getType()).toString());
        // Neo: Permit cancellation of Entity#tick via EntityTickEvent.Pre
        if (!net.neoforged.neoforge.event.EventHooks.fireEntityTickPre(p_entity).isCanceled()) {
            p_entity.tick();
            net.neoforged.neoforge.event.EventHooks.fireEntityTickPost(p_entity);
        }
        this.getProfiler().pop();

        for (Entity entity : p_entity.getPassengers()) {
            this.tickPassenger(p_entity, entity);
        }
    }

    private void tickPassenger(Entity mount, Entity rider) {
        if (rider.isRemoved() || rider.getVehicle() != mount) {
            rider.stopRiding();
        } else if (rider instanceof Player || this.tickingEntities.contains(rider)) {
            rider.setOldPosAndRot();
            rider.tickCount++;
            rider.rideTick();

            for (Entity entity : rider.getPassengers()) {
                this.tickPassenger(rider, entity);
            }
        }
    }

    public void unload(LevelChunk chunk) {
        chunk.clearAllBlockEntities();
        this.chunkSource.getLightEngine().setLightEnabled(chunk.getPos(), false);
        this.entityStorage.stopTicking(chunk.getPos());
    }

    public void onChunkLoaded(ChunkPos chunkPos) {
        this.tintCaches.forEach((p_194154_, p_194155_) -> p_194155_.invalidateForChunk(chunkPos.x, chunkPos.z));
        this.entityStorage.startTicking(chunkPos);
        this.levelRenderer.onChunkLoaded(chunkPos);
    }

    public void clearTintCaches() {
        this.tintCaches.forEach((p_194157_, p_194158_) -> p_194158_.invalidateAll());
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    public int getEntityCount() {
        return this.entityStorage.count();
    }

    public void addEntity(Entity entity) {
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(entity, this)).isCanceled()) return;
        this.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        this.entityStorage.addEntity(entity);
        entity.onAddedToLevel();
    }

    public void removeEntity(int entityId, Entity.RemovalReason reason) {
        Entity entity = this.getEntities().get(entityId);
        if (entity != null) {
            entity.setRemoved(reason);
            entity.onClientRemoval();
        }
    }

    /**
     * Returns the Entity with the given ID, or null if it doesn't exist in this World.
     */
    @Nullable
    @Override
    public Entity getEntity(int id) {
        return this.getEntities().get(id);
    }

    @Override
    public void disconnect() {
        this.connection.getConnection().disconnect(Component.translatable("multiplayer.status.quitting"));
    }

    public void animateTick(int posX, int posY, int posZ) {
        int i = 32;
        RandomSource randomsource = RandomSource.create();
        Block block = this.getMarkerParticleTarget();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < 667; j++) {
            this.doAnimateTick(posX, posY, posZ, 16, randomsource, block, blockpos$mutableblockpos);
            this.doAnimateTick(posX, posY, posZ, 32, randomsource, block, blockpos$mutableblockpos);
        }
    }

    @Nullable
    private Block getMarkerParticleTarget() {
        if (this.minecraft.gameMode.getPlayerMode() == GameType.CREATIVE) {
            ItemStack itemstack = this.minecraft.player.getMainHandItem();
            Item item = itemstack.getItem();
            if (MARKER_PARTICLE_ITEMS.contains(item) && item instanceof BlockItem blockitem) {
                return blockitem.getBlock();
            }
        }

        return null;
    }

    public void doAnimateTick(
        int posX, int posY, int posZ, int range, RandomSource random, @Nullable Block block, BlockPos.MutableBlockPos blockPos
    ) {
        int i = posX + this.random.nextInt(range) - this.random.nextInt(range);
        int j = posY + this.random.nextInt(range) - this.random.nextInt(range);
        int k = posZ + this.random.nextInt(range) - this.random.nextInt(range);
        blockPos.set(i, j, k);
        BlockState blockstate = this.getBlockState(blockPos);
        blockstate.getBlock().animateTick(blockstate, this, blockPos, random);
        FluidState fluidstate = this.getFluidState(blockPos);
        if (!fluidstate.isEmpty()) {
            fluidstate.animateTick(this, blockPos, random);
            ParticleOptions particleoptions = fluidstate.getDripParticle();
            if (particleoptions != null && this.random.nextInt(10) == 0) {
                boolean flag = blockstate.isFaceSturdy(this, blockPos, Direction.DOWN);
                BlockPos blockpos = blockPos.below();
                this.trySpawnDripParticles(blockpos, this.getBlockState(blockpos), particleoptions, flag);
            }
        }

        if (block == blockstate.getBlock()) {
            this.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, blockstate), (double)i + 0.5, (double)j + 0.5, (double)k + 0.5, 0.0, 0.0, 0.0);
        }

        if (!blockstate.isCollisionShapeFullBlock(this, blockPos)) {
            this.getBiome(blockPos)
                .value()
                .getAmbientParticle()
                .ifPresent(
                    p_264703_ -> {
                        if (p_264703_.canSpawn(this.random)) {
                            this.addParticle(
                                p_264703_.getOptions(),
                                (double)blockPos.getX() + this.random.nextDouble(),
                                (double)blockPos.getY() + this.random.nextDouble(),
                                (double)blockPos.getZ() + this.random.nextDouble(),
                                0.0,
                                0.0,
                                0.0
                            );
                        }
                    }
                );
        }
    }

    private void trySpawnDripParticles(BlockPos blockPos, BlockState blockState, ParticleOptions particleData, boolean shapeDownSolid) {
        if (blockState.getFluidState().isEmpty()) {
            VoxelShape voxelshape = blockState.getCollisionShape(this, blockPos);
            double d0 = voxelshape.max(Direction.Axis.Y);
            if (d0 < 1.0) {
                if (shapeDownSolid) {
                    this.spawnFluidParticle(
                        (double)blockPos.getX(),
                        (double)(blockPos.getX() + 1),
                        (double)blockPos.getZ(),
                        (double)(blockPos.getZ() + 1),
                        (double)(blockPos.getY() + 1) - 0.05,
                        particleData
                    );
                }
            } else if (!blockState.is(BlockTags.IMPERMEABLE)) {
                double d1 = voxelshape.min(Direction.Axis.Y);
                if (d1 > 0.0) {
                    this.spawnParticle(blockPos, particleData, voxelshape, (double)blockPos.getY() + d1 - 0.05);
                } else {
                    BlockPos blockpos = blockPos.below();
                    BlockState blockstate = this.getBlockState(blockpos);
                    VoxelShape voxelshape1 = blockstate.getCollisionShape(this, blockpos);
                    double d2 = voxelshape1.max(Direction.Axis.Y);
                    if (d2 < 1.0 && blockstate.getFluidState().isEmpty()) {
                        this.spawnParticle(blockPos, particleData, voxelshape, (double)blockPos.getY() - 0.05);
                    }
                }
            }
        }
    }

    private void spawnParticle(BlockPos pos, ParticleOptions particleData, VoxelShape voxelShape, double y) {
        this.spawnFluidParticle(
            (double)pos.getX() + voxelShape.min(Direction.Axis.X),
            (double)pos.getX() + voxelShape.max(Direction.Axis.X),
            (double)pos.getZ() + voxelShape.min(Direction.Axis.Z),
            (double)pos.getZ() + voxelShape.max(Direction.Axis.Z),
            y,
            particleData
        );
    }

    private void spawnFluidParticle(double xStart, double xEnd, double zStart, double zEnd, double y, ParticleOptions particleData) {
        this.addParticle(
            particleData,
            Mth.lerp(this.random.nextDouble(), xStart, xEnd),
            y,
            Mth.lerp(this.random.nextDouble(), zStart, zEnd),
            0.0,
            0.0,
            0.0
        );
    }

    /**
     * Adds some basic stats of the world to the given crash report.
     */
    @Override
    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashreportcategory = super.fillReportDetails(report);
        crashreportcategory.setDetail("Server brand", () -> this.minecraft.player.connection.serverBrand());
        crashreportcategory.setDetail(
            "Server type", () -> this.minecraft.getSingleplayerServer() == null ? "Non-integrated multiplayer server" : "Integrated singleplayer server"
        );
        crashreportcategory.setDetail("Tracked entity count", () -> String.valueOf(this.getEntityCount()));
        return crashreportcategory;
    }

    @Override
    public void playSeededSound(
        @Nullable Player player,
        double x,
        double y,
        double z,
        Holder<SoundEvent> sound,
        SoundSource category,
        float volume,
        float pitch,
        long seed
    ) {
        net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition event = net.neoforged.neoforge.event.EventHooks.onPlaySoundAtPosition(this, x, y, z, sound, category, volume, pitch);
        if (event.isCanceled() || event.getSound() == null) return;
        sound = event.getSound();
        category = event.getSource();
        volume = event.getNewVolume();
        pitch = event.getNewPitch();

        if (player == this.minecraft.player) {
            this.playSound(x, y, z, sound.value(), category, volume, pitch, false, seed);
        }
    }

    @Override
    public void playSeededSound(
        @Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed
    ) {
        net.neoforged.neoforge.event.PlayLevelSoundEvent.AtEntity event = net.neoforged.neoforge.event.EventHooks.onPlaySoundAtEntity(entity, sound, category, volume, pitch);
        if (event.isCanceled() || event.getSound() == null) return;
        sound = event.getSound();
        category = event.getSource();
        volume = event.getNewVolume();
        pitch = event.getNewPitch();
        if (player == this.minecraft.player) {
            this.minecraft.getSoundManager().play(new EntityBoundSoundInstance(sound.value(), category, volume, pitch, entity, seed));
        }
    }

    @Override
    public void playLocalSound(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.minecraft.getSoundManager().play(new EntityBoundSoundInstance(sound, category, volume, pitch, entity, this.random.nextLong()));
    }

    @Override
    public void playLocalSound(
        double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, boolean distanceDelay
    ) {
        this.playSound(x, y, z, sound, category, volume, pitch, distanceDelay, this.random.nextLong());
    }

    private void playSound(
        double x,
        double y,
        double z,
        SoundEvent soundEvent,
        SoundSource source,
        float volume,
        float pitch,
        boolean distanceDelay,
        long seed
    ) {
        double d0 = this.minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z);
        SimpleSoundInstance simplesoundinstance = new SimpleSoundInstance(
            soundEvent, source, volume, pitch, RandomSource.create(seed), x, y, z
        );
        if (distanceDelay && d0 > 100.0) {
            double d1 = Math.sqrt(d0) / 40.0;
            this.minecraft.getSoundManager().playDelayed(simplesoundinstance, (int)(d1 * 20.0));
        } else {
            this.minecraft.getSoundManager().play(simplesoundinstance);
        }
    }

    @Override
    public void createFireworks(
        double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, List<FireworkExplosion> explosions
    ) {
        if (explosions.isEmpty()) {
            for (int i = 0; i < this.random.nextInt(3) + 2; i++) {
                this.addParticle(
                    ParticleTypes.POOF, x, y, z, this.random.nextGaussian() * 0.05, 0.005, this.random.nextGaussian() * 0.05
                );
            }
        } else {
            this.minecraft
                .particleEngine
                .add(
                    new FireworkParticles.Starter(
                        this, x, y, z, xSpeed, ySpeed, zSpeed, this.minecraft.particleEngine, explosions
                    )
                );
        }
    }

    @Override
    public void sendPacketToServer(Packet<?> packet) {
        this.connection.send(packet);
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.connection.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    public ClientChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(MapId mapId) {
        return this.mapData.get(mapId);
    }

    public void overrideMapData(MapId mapId, MapItemSavedData mapData) {
        this.mapData.put(mapId, mapData);
    }

    @Override
    public void setMapData(MapId mapId, MapItemSavedData mapData) {
    }

    @Override
    public MapId getFreeMapId() {
        return new MapId(0);
    }

    @Override
    public Scoreboard getScoreboard() {
        return this.connection.scoreboard();
    }

    /**
     * Flags are as in setBlockState
     */
    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        this.levelRenderer.blockChanged(this, pos, oldState, newState, flags);
    }

    @Override
    public void setBlocksDirty(BlockPos blockPos, BlockState oldState, BlockState newState) {
        this.levelRenderer.setBlockDirty(blockPos, oldState, newState);
    }

    public void setSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ) {
        this.levelRenderer.setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ);
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        this.levelRenderer.destroyBlockProgress(breakerId, pos, progress);
    }

    @Override
    public void globalLevelEvent(int id, BlockPos pos, int data) {
        this.levelRenderer.globalLevelEvent(id, pos, data);
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
        try {
            this.levelRenderer.levelEvent(type, pos, data);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Playing level event");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Level event being played");
            crashreportcategory.setDetail("Block coordinates", CrashReportCategory.formatLocation(this, pos));
            crashreportcategory.setDetail("Event source", player);
            crashreportcategory.setDetail("Event type", type);
            crashreportcategory.setDetail("Event data", data);
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public void addParticle(
        ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        this.levelRenderer.addParticle(particleData, particleData.getType().getOverrideLimiter(), x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void addParticle(
        ParticleOptions particleData,
        boolean forceAlwaysRender,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed
    ) {
        this.levelRenderer
            .addParticle(particleData, particleData.getType().getOverrideLimiter() || forceAlwaysRender, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void addAlwaysVisibleParticle(
        ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        this.levelRenderer.addParticle(particleData, false, true, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void addAlwaysVisibleParticle(
        ParticleOptions particleData,
        boolean ignoreRange,
        double x,
        double y,
        double z,
        double xSpeed,
        double ySpeed,
        double zSpeed
    ) {
        this.levelRenderer
            .addParticle(
                particleData, particleData.getType().getOverrideLimiter() || ignoreRange, true, x, y, z, xSpeed, ySpeed, zSpeed
            );
    }

    @Override
    public List<AbstractClientPlayer> players() {
        return this.players;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
    }

    public float getSkyDarken(float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        float f1 = 1.0F - (Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.2F);
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 *= 1.0F - this.getRainLevel(partialTick) * 5.0F / 16.0F;
        f1 *= 1.0F - this.getThunderLevel(partialTick) * 5.0F / 16.0F;
        return f1 * 0.8F + 0.2F;
    }

    public Vec3 getSkyColor(Vec3 pos, float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        Vec3 vec3 = pos.subtract(2.0, 2.0, 2.0).scale(0.25);
        BiomeManager biomemanager = this.getBiomeManager();
        Vec3 vec31 = CubicSampler.gaussianSampleVec3(
            vec3, (p_194161_, p_194162_, p_194163_) -> Vec3.fromRGB24(biomemanager.getNoiseBiomeAtQuart(p_194161_, p_194162_, p_194163_).value().getSkyColor())
        );
        float f1 = Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.5F;
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        float f2 = (float)vec31.x * f1;
        float f3 = (float)vec31.y * f1;
        float f4 = (float)vec31.z * f1;
        float f5 = this.getRainLevel(partialTick);
        if (f5 > 0.0F) {
            float f6 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.6F;
            float f7 = 1.0F - f5 * 0.75F;
            f2 = f2 * f7 + f6 * (1.0F - f7);
            f3 = f3 * f7 + f6 * (1.0F - f7);
            f4 = f4 * f7 + f6 * (1.0F - f7);
        }

        float f9 = this.getThunderLevel(partialTick);
        if (f9 > 0.0F) {
            float f10 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.2F;
            float f8 = 1.0F - f9 * 0.75F;
            f2 = f2 * f8 + f10 * (1.0F - f8);
            f3 = f3 * f8 + f10 * (1.0F - f8);
            f4 = f4 * f8 + f10 * (1.0F - f8);
        }

        int i = this.getSkyFlashTime();
        if (i > 0) {
            float f11 = (float)i - partialTick;
            if (f11 > 1.0F) {
                f11 = 1.0F;
            }

            f11 *= 0.45F;
            f2 = f2 * (1.0F - f11) + 0.8F * f11;
            f3 = f3 * (1.0F - f11) + 0.8F * f11;
            f4 = f4 * (1.0F - f11) + 1.0F * f11;
        }

        return new Vec3((double)f2, (double)f3, (double)f4);
    }

    public Vec3 getCloudColor(float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        float f1 = Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.5F;
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        float f2 = 1.0F;
        float f3 = 1.0F;
        float f4 = 1.0F;
        float f5 = this.getRainLevel(partialTick);
        if (f5 > 0.0F) {
            float f6 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.6F;
            float f7 = 1.0F - f5 * 0.95F;
            f2 = f2 * f7 + f6 * (1.0F - f7);
            f3 = f3 * f7 + f6 * (1.0F - f7);
            f4 = f4 * f7 + f6 * (1.0F - f7);
        }

        f2 *= f1 * 0.9F + 0.1F;
        f3 *= f1 * 0.9F + 0.1F;
        f4 *= f1 * 0.85F + 0.15F;
        float f9 = this.getThunderLevel(partialTick);
        if (f9 > 0.0F) {
            float f10 = (f2 * 0.3F + f3 * 0.59F + f4 * 0.11F) * 0.2F;
            float f8 = 1.0F - f9 * 0.95F;
            f2 = f2 * f8 + f10 * (1.0F - f8);
            f3 = f3 * f8 + f10 * (1.0F - f8);
            f4 = f4 * f8 + f10 * (1.0F - f8);
        }

        return new Vec3((double)f2, (double)f3, (double)f4);
    }

    public float getStarBrightness(float partialTick) {
        float f = this.getTimeOfDay(partialTick);
        float f1 = 1.0F - (Mth.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.25F);
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        return f1 * f1 * 0.5F;
    }

    public int getSkyFlashTime() {
        return this.minecraft.options.hideLightningFlash().get() ? 0 : this.skyFlashTime;
    }

    @Override
    public void setSkyFlashTime(int timeFlash) {
        this.skyFlashTime = timeFlash;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        boolean flag = this.effects().constantAmbientLight();
        if (!shade) {
            return flag ? 0.9F : 1.0F;
        } else {
            switch (direction) {
                case DOWN:
                    return flag ? 0.9F : 0.5F;
                case UP:
                    return flag ? 0.9F : 1.0F;
                case NORTH:
                case SOUTH:
                    return 0.8F;
                case WEST:
                case EAST:
                    return 0.6F;
                default:
                    return 1.0F;
            }
        }
    }

    @Override
    public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
        BlockTintCache blocktintcache = this.tintCaches.get(colorResolver);
        return blocktintcache.getColor(blockPos);
    }

    public int calculateBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
        int i = Minecraft.getInstance().options.biomeBlendRadius().get();
        if (i == 0) {
            return colorResolver.getColor(this.getBiome(blockPos).value(), (double)blockPos.getX(), (double)blockPos.getZ());
        } else {
            int j = (i * 2 + 1) * (i * 2 + 1);
            int k = 0;
            int l = 0;
            int i1 = 0;
            Cursor3D cursor3d = new Cursor3D(
                blockPos.getX() - i, blockPos.getY(), blockPos.getZ() - i, blockPos.getX() + i, blockPos.getY(), blockPos.getZ() + i
            );
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            while (cursor3d.advance()) {
                blockpos$mutableblockpos.set(cursor3d.nextX(), cursor3d.nextY(), cursor3d.nextZ());
                int j1 = colorResolver.getColor(
                    this.getBiome(blockpos$mutableblockpos).value(), (double)blockpos$mutableblockpos.getX(), (double)blockpos$mutableblockpos.getZ()
                );
                k += (j1 & 0xFF0000) >> 16;
                l += (j1 & 0xFF00) >> 8;
                i1 += j1 & 0xFF;
            }

            return (k / j & 0xFF) << 16 | (l / j & 0xFF) << 8 | i1 / j & 0xFF;
        }
    }

    public void setDefaultSpawnPos(BlockPos spawnPos, float spawnAngle) {
        this.levelData.setSpawn(spawnPos, spawnAngle);
    }

    @Override
    public String toString() {
        return "ClientLevel";
    }

    public ClientLevel.ClientLevelData getLevelData() {
        return this.clientLevelData;
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
    }

    protected Map<MapId, MapItemSavedData> getAllMapData() {
        return ImmutableMap.copyOf(this.mapData);
    }

    protected void addMapData(Map<MapId, MapItemSavedData> map) {
        this.mapData.putAll(map);
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return this.entityStorage.getEntityGetter();
    }

    @Override
    public String gatherChunkSourceStats() {
        return "Chunks[C] W: " + this.chunkSource.gatherStats() + " E: " + this.entityStorage.gatherStats();
    }

    @Override
    public void addDestroyBlockEffect(BlockPos pos, BlockState state) {
        this.minecraft.particleEngine.destroy(pos, state);
    }

    public void setServerSimulationDistance(int serverSimulationDistance) {
        this.serverSimulationDistance = serverSimulationDistance;
    }

    public int getServerSimulationDistance() {
        return this.serverSimulationDistance;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.connection.enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return this.connection.potionBrewing();
    }

    @OnlyIn(Dist.CLIENT)
    public static class ClientLevelData implements WritableLevelData {
        private final boolean hardcore;
        private final GameRules gameRules;
        private final boolean isFlat;
        private BlockPos spawnPos;
        private float spawnAngle;
        private long gameTime;
        private long dayTime;
        private boolean raining;
        private Difficulty difficulty;
        private boolean difficultyLocked;

        public ClientLevelData(Difficulty difficulty, boolean hardcore, boolean isFlat) {
            this.difficulty = difficulty;
            this.hardcore = hardcore;
            this.isFlat = isFlat;
            this.gameRules = new GameRules();
        }

        @Override
        public BlockPos getSpawnPos() {
            return this.spawnPos;
        }

        @Override
        public float getSpawnAngle() {
            return this.spawnAngle;
        }

        @Override
        public long getGameTime() {
            return this.gameTime;
        }

        @Override
        public long getDayTime() {
            return this.dayTime;
        }

        public void setGameTime(long gameTime) {
            this.gameTime = gameTime;
        }

        public void setDayTime(long dayTime) {
            this.dayTime = dayTime;
        }

        @Override
        public void setSpawn(BlockPos spawnPoint, float angle) {
            this.spawnPos = spawnPoint.immutable();
            this.spawnAngle = angle;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public boolean isRaining() {
            return this.raining;
        }

        /**
         * Sets whether it is raining or not.
         */
        @Override
        public void setRaining(boolean isRaining) {
            this.raining = isRaining;
        }

        @Override
        public boolean isHardcore() {
            return this.hardcore;
        }

        @Override
        public GameRules getGameRules() {
            return this.gameRules;
        }

        @Override
        public Difficulty getDifficulty() {
            return this.difficulty;
        }

        @Override
        public boolean isDifficultyLocked() {
            return this.difficultyLocked;
        }

        @Override
        public void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor level) {
            WritableLevelData.super.fillCrashReportCategory(crashReportCategory, level);
        }

        public void setDifficulty(Difficulty difficulty) {
            net.neoforged.neoforge.common.CommonHooks.onDifficultyChange(difficulty, this.difficulty);
            this.difficulty = difficulty;
        }

        public void setDifficultyLocked(boolean difficultyLocked) {
            this.difficultyLocked = difficultyLocked;
        }

        public double getHorizonHeight(LevelHeightAccessor level) {
            return this.isFlat ? (double)level.getMinBuildHeight() : 63.0;
        }

        public float getClearColorScale() {
            return this.isFlat ? 1.0F : 0.03125F;
        }
    }

    @OnlyIn(Dist.CLIENT)
    final class EntityCallbacks implements LevelCallback<Entity> {
        public void onCreated(Entity p_171696_) {
        }

        public void onDestroyed(Entity p_171700_) {
        }

        public void onTickingStart(Entity p_171704_) {
            ClientLevel.this.tickingEntities.add(p_171704_);
        }

        public void onTickingEnd(Entity p_171708_) {
            ClientLevel.this.tickingEntities.remove(p_171708_);
        }

        public void onTrackingStart(Entity p_171712_) {
            if (p_171712_ instanceof AbstractClientPlayer) {
                ClientLevel.this.players.add((AbstractClientPlayer)p_171712_);
            }
            if (p_171712_.isMultipartEntity()) {
                for (net.neoforged.neoforge.entity.PartEntity<?> part : p_171712_.getParts()) {
                    ClientLevel.this.partEntities.put(part.getId(), part);
                }
            }
        }

        public void onTrackingEnd(Entity p_171716_) {
            p_171716_.unRide();
            ClientLevel.this.players.remove(p_171716_);

            p_171716_.onRemovedFromLevel();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent(p_171716_, ClientLevel.this));

            if (p_171716_.isMultipartEntity()) {
                for (net.neoforged.neoforge.entity.PartEntity<?> part : p_171716_.getParts()) {
                    ClientLevel.this.partEntities.remove(part.getId());
                }
            }
        }

        public void onSectionChange(Entity p_233660_) {
        }
    }

    @Override
    public java.util.Collection<net.neoforged.neoforge.entity.PartEntity<?>> getPartEntities() {
        return this.partEntities.values();
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelDataManager getModelDataManager() {
        return modelDataManager;
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData(BlockPos pos) {
        return modelDataManager.getAt(pos);
    }

    @Override
    public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
        boolean constantAmbientLight = this.effects().constantAmbientLight();
        if (!shade)
            return constantAmbientLight ? 0.9F : 1.0F;
        return net.neoforged.neoforge.client.model.lighting.QuadLighter.calculateShade(normalX, normalY, normalZ, constantAmbientLight);
    }

    // Neo: Variable day time code

    private float dayTimeFraction = 0.0f;
    private float dayTimePerTick = -1.0f;

    @org.jetbrains.annotations.ApiStatus.Internal
    public void setDayTimeFraction(float dayTimeFraction) {
        this.dayTimeFraction = dayTimeFraction;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public float getDayTimeFraction() {
        return dayTimeFraction;
    }

    public float getDayTimePerTick() {
        return dayTimePerTick;
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public void setDayTimePerTick(float dayTimePerTick) {
        this.dayTimePerTick = dayTimePerTick;
    }
}
