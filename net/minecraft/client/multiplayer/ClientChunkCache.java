package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientChunkCache extends ChunkSource {
    static final Logger LOGGER = LogUtils.getLogger();
    private final LevelChunk emptyChunk;
    private final LevelLightEngine lightEngine;
    volatile ClientChunkCache.Storage storage;
    final ClientLevel level;

    public ClientChunkCache(ClientLevel level, int viewDistance) {
        this.level = level;
        this.emptyChunk = new EmptyLevelChunk(
            level, new ChunkPos(0, 0), level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS)
        );
        this.lightEngine = new LevelLightEngine(this, true, level.dimensionType().hasSkyLight());
        this.storage = new ClientChunkCache.Storage(calculateStorageRange(viewDistance));
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    private static boolean isValidChunk(@Nullable LevelChunk chunk, int x, int z) {
        if (chunk == null) {
            return false;
        } else {
            ChunkPos chunkpos = chunk.getPos();
            return chunkpos.x == x && chunkpos.z == z;
        }
    }

    public void drop(ChunkPos chunkPos) {
        if (this.storage.inRange(chunkPos.x, chunkPos.z)) {
            int i = this.storage.getIndex(chunkPos.x, chunkPos.z);
            LevelChunk levelchunk = this.storage.getChunk(i);
            if (isValidChunk(levelchunk, chunkPos.x, chunkPos.z)) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(levelchunk));
                this.storage.replace(i, levelchunk, null);
            }
        }
    }

    @Nullable
    public LevelChunk getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        if (this.storage.inRange(x, z)) {
            LevelChunk levelchunk = this.storage.getChunk(this.storage.getIndex(x, z));
            if (isValidChunk(levelchunk, x, z)) {
                return levelchunk;
            }
        }

        return requireChunk ? this.emptyChunk : null;
    }

    @Override
    public BlockGetter getLevel() {
        return this.level;
    }

    public void replaceBiomes(int x, int z, FriendlyByteBuf buffer) {
        if (!this.storage.inRange(x, z)) {
            LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", x, z);
        } else {
            int i = this.storage.getIndex(x, z);
            LevelChunk levelchunk = this.storage.chunks.get(i);
            if (!isValidChunk(levelchunk, x, z)) {
                LOGGER.warn("Ignoring chunk since it's not present: {}, {}", x, z);
            } else {
                levelchunk.replaceBiomes(buffer);
            }
        }
    }

    @Nullable
    public LevelChunk replaceWithPacketData(
        int x,
        int z,
        FriendlyByteBuf buffer,
        CompoundTag tag,
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
    ) {
        if (!this.storage.inRange(x, z)) {
            LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", x, z);
            return null;
        } else {
            int i = this.storage.getIndex(x, z);
            LevelChunk levelchunk = this.storage.chunks.get(i);
            ChunkPos chunkpos = new ChunkPos(x, z);
            if (!isValidChunk(levelchunk, x, z)) {
                levelchunk = new LevelChunk(this.level, chunkpos);
                levelchunk.replaceWithPacketData(buffer, tag, consumer);
                this.storage.replace(i, levelchunk);
            } else {
                levelchunk.replaceWithPacketData(buffer, tag, consumer);
            }

            this.level.onChunkLoaded(chunkpos);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(levelchunk, false));
            return levelchunk;
        }
    }

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
    }

    public void updateViewCenter(int x, int z) {
        this.storage.viewCenterX = x;
        this.storage.viewCenterZ = z;
    }

    public void updateViewRadius(int viewDistance) {
        int i = this.storage.chunkRadius;
        int j = calculateStorageRange(viewDistance);
        if (i != j) {
            ClientChunkCache.Storage clientchunkcache$storage = new ClientChunkCache.Storage(j);
            clientchunkcache$storage.viewCenterX = this.storage.viewCenterX;
            clientchunkcache$storage.viewCenterZ = this.storage.viewCenterZ;

            for (int k = 0; k < this.storage.chunks.length(); k++) {
                LevelChunk levelchunk = this.storage.chunks.get(k);
                if (levelchunk != null) {
                    ChunkPos chunkpos = levelchunk.getPos();
                    if (clientchunkcache$storage.inRange(chunkpos.x, chunkpos.z)) {
                        clientchunkcache$storage.replace(clientchunkcache$storage.getIndex(chunkpos.x, chunkpos.z), levelchunk);
                    }
                }
            }

            this.storage = clientchunkcache$storage;
        }
    }

    private static int calculateStorageRange(int viewDistance) {
        return Math.max(2, viewDistance) + 3;
    }

    @Override
    public String gatherStats() {
        return this.storage.chunks.length() + ", " + this.getLoadedChunksCount();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.storage.chunkCount;
    }

    @Override
    public void onLightUpdate(LightLayer type, SectionPos pos) {
        Minecraft.getInstance().levelRenderer.setSectionDirty(pos.x(), pos.y(), pos.z());
    }

    @OnlyIn(Dist.CLIENT)
    final class Storage {
        final AtomicReferenceArray<LevelChunk> chunks;
        final int chunkRadius;
        private final int viewRange;
        volatile int viewCenterX;
        volatile int viewCenterZ;
        int chunkCount;

        Storage(int chunkRadius) {
            this.chunkRadius = chunkRadius;
            this.viewRange = chunkRadius * 2 + 1;
            this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange);
        }

        int getIndex(int x, int z) {
            return Math.floorMod(z, this.viewRange) * this.viewRange + Math.floorMod(x, this.viewRange);
        }

        protected void replace(int chunkIndex, @Nullable LevelChunk chunk) {
            LevelChunk levelchunk = this.chunks.getAndSet(chunkIndex, chunk);
            if (levelchunk != null) {
                this.chunkCount--;
                ClientChunkCache.this.level.unload(levelchunk);
            }

            if (chunk != null) {
                this.chunkCount++;
            }
        }

        protected LevelChunk replace(int chunkIndex, LevelChunk chunk, @Nullable LevelChunk replaceWith) {
            if (this.chunks.compareAndSet(chunkIndex, chunk, replaceWith) && replaceWith == null) {
                this.chunkCount--;
            }

            ClientChunkCache.this.level.unload(chunk);
            return chunk;
        }

        boolean inRange(int x, int z) {
            return Math.abs(x - this.viewCenterX) <= this.chunkRadius && Math.abs(z - this.viewCenterZ) <= this.chunkRadius;
        }

        @Nullable
        protected LevelChunk getChunk(int chunkIndex) {
            return this.chunks.get(chunkIndex);
        }

        private void dumpChunks(String filePath) {
            try (FileOutputStream fileoutputstream = new FileOutputStream(filePath)) {
                int i = ClientChunkCache.this.storage.chunkRadius;

                for (int j = this.viewCenterZ - i; j <= this.viewCenterZ + i; j++) {
                    for (int k = this.viewCenterX - i; k <= this.viewCenterX + i; k++) {
                        LevelChunk levelchunk = ClientChunkCache.this.storage.chunks.get(ClientChunkCache.this.storage.getIndex(k, j));
                        if (levelchunk != null) {
                            ChunkPos chunkpos = levelchunk.getPos();
                            fileoutputstream.write((chunkpos.x + "\t" + chunkpos.z + "\t" + levelchunk.isEmpty() + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (IOException ioexception) {
                ClientChunkCache.LOGGER.error("Failed to dump chunks to file {}", filePath, ioexception);
            }
        }
    }
}
