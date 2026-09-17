package net.minecraft.server.level;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class ChunkTracker extends DynamicGraphMinFixedPoint {
    protected ChunkTracker(int firstQueuedLevel, int width, int height) {
        super(firstQueuedLevel, width, height);
    }

    @Override
    protected boolean isSource(long pos) {
        return pos == ChunkPos.INVALID_CHUNK_POS;
    }

    @Override
    protected void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing) {
        if (!isDecreasing || level < this.levelCount - 2) {
            ChunkPos chunkpos = new ChunkPos(pos);
            int i = chunkpos.x;
            int j = chunkpos.z;

            for (int k = -1; k <= 1; k++) {
                for (int l = -1; l <= 1; l++) {
                    long i1 = ChunkPos.asLong(i + k, j + l);
                    if (i1 != pos) {
                        this.checkNeighbor(pos, i1, level, isDecreasing);
                    }
                }
            }
        }
    }

    /**
     * Computes level propagated from neighbors of specified position with given existing level, excluding the given source position.
     */
    @Override
    protected int getComputedLevel(long pos, long excludedSourcePos, int level) {
        int i = level;
        ChunkPos chunkpos = new ChunkPos(pos);
        int j = chunkpos.x;
        int k = chunkpos.z;

        for (int l = -1; l <= 1; l++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                long j1 = ChunkPos.asLong(j + l, k + i1);
                if (j1 == pos) {
                    j1 = ChunkPos.INVALID_CHUNK_POS;
                }

                if (j1 != excludedSourcePos) {
                    int k1 = this.computeLevelFromNeighbor(j1, pos, this.getLevel(j1));
                    if (i > k1) {
                        i = k1;
                    }

                    if (i == 0) {
                        return i;
                    }
                }
            }
        }

        return i;
    }

    /**
     * Returns level propagated from start position with specified level to the neighboring end position.
     */
    @Override
    protected int computeLevelFromNeighbor(long startPos, long endPos, int startLevel) {
        return startPos == ChunkPos.INVALID_CHUNK_POS ? this.getLevelFromSource(endPos) : startLevel + 1;
    }

    protected abstract int getLevelFromSource(long pos);

    public void update(long pos, int level, boolean isDecreasing) {
        this.checkEdge(ChunkPos.INVALID_CHUNK_POS, pos, level, isDecreasing);
    }
}
