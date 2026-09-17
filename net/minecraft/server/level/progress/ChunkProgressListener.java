package net.minecraft.server.level.progress;

import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface ChunkProgressListener {
    void updateSpawnPos(ChunkPos center);

    void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus);

    void start();

    void stop();

    static int calculateDiameter(int radius) {
        return 2 * radius + 1;
    }
}
