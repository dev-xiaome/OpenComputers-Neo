package net.minecraft.server.network;

import com.google.common.collect.Comparators;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public class PlayerChunkSender {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final float MIN_CHUNKS_PER_TICK = 0.01F;
    public static final float MAX_CHUNKS_PER_TICK = 64.0F;
    private static final float START_CHUNKS_PER_TICK = 9.0F;
    private static final int MAX_UNACKNOWLEDGED_BATCHES = 10;
    private final LongSet pendingChunks = new LongOpenHashSet();
    private final boolean memoryConnection;
    private float desiredChunksPerTick = 9.0F;
    private float batchQuota;
    private int unacknowledgedBatches;
    private int maxUnacknowledgedBatches = 1;

    public PlayerChunkSender(boolean memoryConnection) {
        this.memoryConnection = memoryConnection;
    }

    public void markChunkPendingToSend(LevelChunk chunk) {
        this.pendingChunks.add(chunk.getPos().toLong());
    }

    public void dropChunk(ServerPlayer player, ChunkPos chunkPos) {
        if (!this.pendingChunks.remove(chunkPos.toLong()) && player.isAlive()) {
            player.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos));
        }
    }

    public void sendNextChunks(ServerPlayer player) {
        if (this.unacknowledgedBatches < this.maxUnacknowledgedBatches) {
            float f = Math.max(1.0F, this.desiredChunksPerTick);
            this.batchQuota = Math.min(this.batchQuota + this.desiredChunksPerTick, f);
            if (!(this.batchQuota < 1.0F)) {
                if (!this.pendingChunks.isEmpty()) {
                    ServerLevel serverlevel = player.serverLevel();
                    ChunkMap chunkmap = serverlevel.getChunkSource().chunkMap;
                    List<LevelChunk> list = this.collectChunksToSend(chunkmap, player.chunkPosition());
                    if (!list.isEmpty()) {
                        ServerGamePacketListenerImpl servergamepacketlistenerimpl = player.connection;
                        this.unacknowledgedBatches++;
                        servergamepacketlistenerimpl.send(ClientboundChunkBatchStartPacket.INSTANCE);

                        for (LevelChunk levelchunk : list) {
                            sendChunk(servergamepacketlistenerimpl, serverlevel, levelchunk);
                        }

                        servergamepacketlistenerimpl.send(new ClientboundChunkBatchFinishedPacket(list.size()));
                        this.batchQuota = this.batchQuota - (float)list.size();
                    }
                }
            }
        }
    }

    private static void sendChunk(ServerGamePacketListenerImpl packetListener, ServerLevel level, LevelChunk chunk) {
        packetListener.send(chunk.getAuxLightManager(chunk.getPos()).sendLightDataTo(
                new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null)
        ));
        ChunkPos chunkpos = chunk.getPos();
        DebugPackets.sendPoiPacketsForChunk(level, chunkpos);
        net.neoforged.neoforge.event.EventHooks.fireChunkSent(packetListener.player, chunk, level);
    }

    private List<LevelChunk> collectChunksToSend(ChunkMap chunkMap, ChunkPos chunkPos) {
        int i = Mth.floor(this.batchQuota);
        List<LevelChunk> list;
        if (!this.memoryConnection && this.pendingChunks.size() > i) {
            list = this.pendingChunks
                .stream()
                .collect(Comparators.least(i, Comparator.comparingInt(chunkPos::distanceSquared)))
                .stream()
                .mapToLong(Long::longValue)
                .mapToObj(chunkMap::getChunkToSend)
                .filter(Objects::nonNull)
                .toList();
        } else {
            list = this.pendingChunks
                .longStream()
                .mapToObj(chunkMap::getChunkToSend)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(p_294268_ -> chunkPos.distanceSquared(p_294268_.getPos())))
                .toList();
        }

        for (LevelChunk levelchunk : list) {
            this.pendingChunks.remove(levelchunk.getPos().toLong());
        }

        return list;
    }

    public void onChunkBatchReceivedByClient(float desiredBatchSize) {
        this.unacknowledgedBatches--;
        this.desiredChunksPerTick = Double.isNaN((double)desiredBatchSize) ? 0.01F : Mth.clamp(desiredBatchSize, 0.01F, 64.0F);
        if (this.unacknowledgedBatches == 0) {
            this.batchQuota = 1.0F;
        }

        this.maxUnacknowledgedBatches = 10;
    }

    public boolean isPending(long chunkPos) {
        return this.pendingChunks.contains(chunkPos);
    }
}
