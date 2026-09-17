package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class TheEndGatewayBlockEntity extends TheEndPortalBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SPAWN_TIME = 200;
    private static final int COOLDOWN_TIME = 40;
    private static final int ATTENTION_INTERVAL = 2400;
    private static final int EVENT_COOLDOWN = 1;
    private static final int GATEWAY_HEIGHT_ABOVE_SURFACE = 10;
    private long age;
    private int teleportCooldown;
    @Nullable
    private BlockPos exitPortal;
    private boolean exactTeleport;

    public TheEndGatewayBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.END_GATEWAY, pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Age", this.age);
        if (this.exitPortal != null) {
            tag.put("exit_portal", NbtUtils.writeBlockPos(this.exitPortal));
        }

        if (this.exactTeleport) {
            tag.putBoolean("ExactTeleport", true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.age = tag.getLong("Age");
        NbtUtils.readBlockPos(tag, "exit_portal").filter(Level::isInSpawnableBounds).ifPresent(p_325870_ -> this.exitPortal = p_325870_);
        this.exactTeleport = tag.getBoolean("ExactTeleport");
    }

    public static void beamAnimationTick(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        blockEntity.age++;
        if (blockEntity.isCoolingDown()) {
            blockEntity.teleportCooldown--;
        }
    }

    public static void portalTick(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        boolean flag = blockEntity.isSpawning();
        boolean flag1 = blockEntity.isCoolingDown();
        blockEntity.age++;
        if (flag1) {
            blockEntity.teleportCooldown--;
        } else if (blockEntity.age % 2400L == 0L) {
            triggerCooldown(level, pos, state, blockEntity);
        }

        if (flag != blockEntity.isSpawning() || flag1 != blockEntity.isCoolingDown()) {
            setChanged(level, pos, state);
        }
    }

    public boolean isSpawning() {
        return this.age < 200L;
    }

    public boolean isCoolingDown() {
        return this.teleportCooldown > 0;
    }

    public float getSpawnPercent(float partialTicks) {
        return Mth.clamp(((float)this.age + partialTicks) / 200.0F, 0.0F, 1.0F);
    }

    public float getCooldownPercent(float partialTicks) {
        return 1.0F - Mth.clamp(((float)this.teleportCooldown - partialTicks) / 40.0F, 0.0F, 1.0F);
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public static void triggerCooldown(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        if (!level.isClientSide) {
            blockEntity.teleportCooldown = 40;
            level.blockEvent(pos, state.getBlock(), 1, 0);
            setChanged(level, pos, state);
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.teleportCooldown = 40;
            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    @Nullable
    public Vec3 getPortalPosition(ServerLevel level, BlockPos pos) {
        if (this.exitPortal == null && level.dimension() == Level.END) {
            BlockPos blockpos = findOrCreateValidTeleportPos(level, pos);
            blockpos = blockpos.above(10);
            LOGGER.debug("Creating portal at {}", blockpos);
            spawnGatewayPortal(level, blockpos, EndGatewayConfiguration.knownExit(pos, false));
            this.setExitPosition(blockpos, this.exactTeleport);
        }

        if (this.exitPortal != null) {
            BlockPos blockpos1 = this.exactTeleport ? this.exitPortal : findExitPosition(level, this.exitPortal);
            return blockpos1.getBottomCenter();
        } else {
            return null;
        }
    }

    private static BlockPos findExitPosition(Level level, BlockPos pos) {
        BlockPos blockpos = findTallestBlock(level, pos.offset(0, 2, 0), 5, false);
        LOGGER.debug("Best exit position for portal at {} is {}", pos, blockpos);
        return blockpos.above();
    }

    private static BlockPos findOrCreateValidTeleportPos(ServerLevel level, BlockPos pos) {
        Vec3 vec3 = findExitPortalXZPosTentative(level, pos);
        LevelChunk levelchunk = getChunk(level, vec3);
        BlockPos blockpos = findValidSpawnInChunk(levelchunk);
        if (blockpos == null) {
            BlockPos blockpos1 = BlockPos.containing(vec3.x + 0.5, 75.0, vec3.z + 0.5);
            LOGGER.debug("Failed to find a suitable block to teleport to, spawning an island on {}", blockpos1);
            level.registryAccess()
                .registry(Registries.CONFIGURED_FEATURE)
                .flatMap(p_258975_ -> p_258975_.getHolder(EndFeatures.END_ISLAND))
                .ifPresent(
                    p_256040_ -> p_256040_.value()
                            .place(level, level.getChunkSource().getGenerator(), RandomSource.create(blockpos1.asLong()), blockpos1)
                );
            blockpos = blockpos1;
        } else {
            LOGGER.debug("Found suitable block to teleport to: {}", blockpos);
        }

        return findTallestBlock(level, blockpos, 16, true);
    }

    private static Vec3 findExitPortalXZPosTentative(ServerLevel level, BlockPos pos) {
        Vec3 vec3 = new Vec3((double)pos.getX(), 0.0, (double)pos.getZ()).normalize();
        int i = 1024;
        Vec3 vec31 = vec3.scale(1024.0);

        for (int j = 16; !isChunkEmpty(level, vec31) && j-- > 0; vec31 = vec31.add(vec3.scale(-16.0))) {
            LOGGER.debug("Skipping backwards past nonempty chunk at {}", vec31);
        }

        for (int k = 16; isChunkEmpty(level, vec31) && k-- > 0; vec31 = vec31.add(vec3.scale(16.0))) {
            LOGGER.debug("Skipping forward past empty chunk at {}", vec31);
        }

        LOGGER.debug("Found chunk at {}", vec31);
        return vec31;
    }

    private static boolean isChunkEmpty(ServerLevel level, Vec3 pos) {
        return getChunk(level, pos).getHighestFilledSectionIndex() == -1;
    }

    private static BlockPos findTallestBlock(BlockGetter level, BlockPos pos, int radius, boolean allowBedrock) {
        BlockPos blockpos = null;

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if (i != 0 || j != 0 || allowBedrock) {
                    for (int k = level.getMaxBuildHeight() - 1; k > (blockpos == null ? level.getMinBuildHeight() : blockpos.getY()); k--) {
                        BlockPos blockpos1 = new BlockPos(pos.getX() + i, k, pos.getZ() + j);
                        BlockState blockstate = level.getBlockState(blockpos1);
                        if (blockstate.isCollisionShapeFullBlock(level, blockpos1) && (allowBedrock || !blockstate.is(Blocks.BEDROCK))) {
                            blockpos = blockpos1;
                            break;
                        }
                    }
                }
            }
        }

        return blockpos == null ? pos : blockpos;
    }

    private static LevelChunk getChunk(Level level, Vec3 pos) {
        return level.getChunk(Mth.floor(pos.x / 16.0), Mth.floor(pos.z / 16.0));
    }

    @Nullable
    private static BlockPos findValidSpawnInChunk(LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        BlockPos blockpos = new BlockPos(chunkpos.getMinBlockX(), 30, chunkpos.getMinBlockZ());
        int i = chunk.getHighestSectionPosition() + 16 - 1;
        BlockPos blockpos1 = new BlockPos(chunkpos.getMaxBlockX(), i, chunkpos.getMaxBlockZ());
        BlockPos blockpos2 = null;
        double d0 = 0.0;

        for (BlockPos blockpos3 : BlockPos.betweenClosed(blockpos, blockpos1)) {
            BlockState blockstate = chunk.getBlockState(blockpos3);
            BlockPos blockpos4 = blockpos3.above();
            BlockPos blockpos5 = blockpos3.above(2);
            if (blockstate.is(Blocks.END_STONE)
                && !chunk.getBlockState(blockpos4).isCollisionShapeFullBlock(chunk, blockpos4)
                && !chunk.getBlockState(blockpos5).isCollisionShapeFullBlock(chunk, blockpos5)) {
                double d1 = blockpos3.distToCenterSqr(0.0, 0.0, 0.0);
                if (blockpos2 == null || d1 < d0) {
                    blockpos2 = blockpos3;
                    d0 = d1;
                }
            }
        }

        return blockpos2;
    }

    private static void spawnGatewayPortal(ServerLevel level, BlockPos pos, EndGatewayConfiguration config) {
        Feature.END_GATEWAY.place(config, level, level.getChunkSource().getGenerator(), RandomSource.create(), pos);
    }

    @Override
    public boolean shouldRenderFace(Direction face) {
        return Block.shouldRenderFace(this.getBlockState(), this.level, this.getBlockPos(), face, this.getBlockPos().relative(face));
    }

    public int getParticleAmount() {
        int i = 0;

        for (Direction direction : Direction.values()) {
            i += this.shouldRenderFace(direction) ? 1 : 0;
        }

        return i;
    }

    public void setExitPosition(BlockPos exitPortal, boolean exactTeleport) {
        this.exactTeleport = exactTeleport;
        this.exitPortal = exitPortal;
        this.setChanged();
    }
}
