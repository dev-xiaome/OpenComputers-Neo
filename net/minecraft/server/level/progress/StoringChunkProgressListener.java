package net.minecraft.server.level.progress;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class StoringChunkProgressListener implements ChunkProgressListener {
    private final LoggerChunkProgressListener delegate;
    private final Long2ObjectOpenHashMap<ChunkStatus> statuses = new Long2ObjectOpenHashMap<>();
    private ChunkPos spawnPos = new ChunkPos(0, 0);
    private final int fullDiameter;
    private final int radius;
    private final int diameter;
    private boolean started;

    private StoringChunkProgressListener(LoggerChunkProgressListener delegate, int fullDiameter, int radius, int diameter) {
        this.delegate = delegate;
        this.fullDiameter = fullDiameter;
        this.radius = radius;
        this.diameter = diameter;
    }

    public static StoringChunkProgressListener createFromGameruleRadius(int radius) {
        return radius > 0 ? create(radius + 1) : createCompleted();
    }

    public static StoringChunkProgressListener create(int radius) {
        LoggerChunkProgressListener loggerchunkprogresslistener = LoggerChunkProgressListener.create(radius);
        int i = ChunkProgressListener.calculateDiameter(radius);
        int j = radius + ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        int k = ChunkProgressListener.calculateDiameter(j);
        return new StoringChunkProgressListener(loggerchunkprogresslistener, i, j, k);
    }

    public static StoringChunkProgressListener createCompleted() {
        return new StoringChunkProgressListener(LoggerChunkProgressListener.createCompleted(), 0, 0, 0);
    }

    @Override
    public void updateSpawnPos(ChunkPos center) {
        if (this.started) {
            this.delegate.updateSpawnPos(center);
            this.spawnPos = center;
        }
    }

    @Override
    public void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus) {
        if (this.started) {
            this.delegate.onStatusChange(chunkPos, chunkStatus);
            if (chunkStatus == null) {
                this.statuses.remove(chunkPos.toLong());
            } else {
                this.statuses.put(chunkPos.toLong(), chunkStatus);
            }
        }
    }

    @Override
    public void start() {
        this.started = true;
        this.statuses.clear();
        this.delegate.start();
    }

    @Override
    public void stop() {
        this.started = false;
        this.delegate.stop();
    }

    public int getFullDiameter() {
        return this.fullDiameter;
    }

    public int getDiameter() {
        return this.diameter;
    }

    public int getProgress() {
        return this.delegate.getProgress();
    }

    @Nullable
    public ChunkStatus getStatus(int x, int z) {
        return this.statuses.get(ChunkPos.asLong(x + this.spawnPos.x - this.radius, z + this.spawnPos.z - this.radius));
    }
}
