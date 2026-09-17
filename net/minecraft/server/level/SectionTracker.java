package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class SectionTracker extends DynamicGraphMinFixedPoint {
    protected SectionTracker(int firstQueuedLevel, int width, int height) {
        super(firstQueuedLevel, width, height);
    }

    @Override
    protected void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing) {
        if (!isDecreasing || level < this.levelCount - 2) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    for (int k = -1; k <= 1; k++) {
                        long l = SectionPos.offset(pos, i, j, k);
                        if (l != pos) {
                            this.checkNeighbor(pos, l, level, isDecreasing);
                        }
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

        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                for (int l = -1; l <= 1; l++) {
                    long i1 = SectionPos.offset(pos, j, k, l);
                    if (i1 == pos) {
                        i1 = Long.MAX_VALUE;
                    }

                    if (i1 != excludedSourcePos) {
                        int j1 = this.computeLevelFromNeighbor(i1, pos, this.getLevel(i1));
                        if (i > j1) {
                            i = j1;
                        }

                        if (i == 0) {
                            return i;
                        }
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
        return this.isSource(startPos) ? this.getLevelFromSource(endPos) : startLevel + 1;
    }

    protected abstract int getLevelFromSource(long pos);

    public void update(long pos, int level, boolean isDecreasing) {
        this.checkEdge(Long.MAX_VALUE, pos, level, isDecreasing);
    }
}
