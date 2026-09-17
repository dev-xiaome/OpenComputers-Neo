package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.LongPredicate;
import net.minecraft.util.Mth;

public abstract class DynamicGraphMinFixedPoint {
    public static final long SOURCE = Long.MAX_VALUE;
    private static final int NO_COMPUTED_LEVEL = 255;
    protected final int levelCount;
    private final LeveledPriorityQueue priorityQueue;
    private final Long2ByteMap computedLevels;
    private volatile boolean hasWork;

    protected DynamicGraphMinFixedPoint(int firstQueuedLevel, int width, final int height) {
        if (firstQueuedLevel >= 254) {
            throw new IllegalArgumentException("Level count must be < 254.");
        } else {
            this.levelCount = firstQueuedLevel;
            this.priorityQueue = new LeveledPriorityQueue(firstQueuedLevel, width);
            this.computedLevels = new Long2ByteOpenHashMap(height, 0.5F) {
                @Override
                protected void rehash(int newSize) {
                    if (newSize > height) {
                        super.rehash(newSize);
                    }
                }
            };
            this.computedLevels.defaultReturnValue((byte)-1);
        }
    }

    protected void removeFromQueue(long position) {
        int i = this.computedLevels.remove(position) & 255;
        if (i != 255) {
            int j = this.getLevel(position);
            int k = this.calculatePriority(j, i);
            this.priorityQueue.dequeue(position, k, this.levelCount);
            this.hasWork = !this.priorityQueue.isEmpty();
        }
    }

    public void removeIf(LongPredicate predicate) {
        LongList longlist = new LongArrayList();
        this.computedLevels.keySet().forEach(p_75586_ -> {
            if (predicate.test(p_75586_)) {
                longlist.add(p_75586_);
            }
        });
        longlist.forEach(this::removeFromQueue);
    }

    private int calculatePriority(int oldLevel, int newLevel) {
        return Math.min(Math.min(oldLevel, newLevel), this.levelCount - 1);
    }

    protected void checkNode(long levelPos) {
        this.checkEdge(levelPos, levelPos, this.levelCount - 1, false);
    }

    protected void checkEdge(long fromPos, long toPos, int newLevel, boolean isDecreasing) {
        this.checkEdge(fromPos, toPos, newLevel, this.getLevel(toPos), this.computedLevels.get(toPos) & 255, isDecreasing);
        this.hasWork = !this.priorityQueue.isEmpty();
    }

    private void checkEdge(long fromPos, long toPos, int newLevel, int previousLevel, int propagationLevel, boolean isDecreasing) {
        if (!this.isSource(toPos)) {
            newLevel = Mth.clamp(newLevel, 0, this.levelCount - 1);
            previousLevel = Mth.clamp(previousLevel, 0, this.levelCount - 1);
            boolean flag = propagationLevel == 255;
            if (flag) {
                propagationLevel = previousLevel;
            }

            int i;
            if (isDecreasing) {
                i = Math.min(propagationLevel, newLevel);
            } else {
                i = Mth.clamp(this.getComputedLevel(toPos, fromPos, newLevel), 0, this.levelCount - 1);
            }

            int j = this.calculatePriority(previousLevel, propagationLevel);
            if (previousLevel != i) {
                int k = this.calculatePriority(previousLevel, i);
                if (j != k && !flag) {
                    this.priorityQueue.dequeue(toPos, j, k);
                }

                this.priorityQueue.enqueue(toPos, k);
                this.computedLevels.put(toPos, (byte)i);
            } else if (!flag) {
                this.priorityQueue.dequeue(toPos, j, this.levelCount);
                this.computedLevels.remove(toPos);
            }
        }
    }

    protected final void checkNeighbor(long fromPos, long toPos, int sourceLevel, boolean isDecreasing) {
        int i = this.computedLevels.get(toPos) & 255;
        int j = Mth.clamp(this.computeLevelFromNeighbor(fromPos, toPos, sourceLevel), 0, this.levelCount - 1);
        if (isDecreasing) {
            this.checkEdge(fromPos, toPos, j, this.getLevel(toPos), i, isDecreasing);
        } else {
            boolean flag = i == 255;
            int k;
            if (flag) {
                k = Mth.clamp(this.getLevel(toPos), 0, this.levelCount - 1);
            } else {
                k = i;
            }

            if (j == k) {
                this.checkEdge(fromPos, toPos, this.levelCount - 1, flag ? k : this.getLevel(toPos), i, isDecreasing);
            }
        }
    }

    protected final boolean hasWork() {
        return this.hasWork;
    }

    protected final int runUpdates(int toUpdateCount) {
        if (this.priorityQueue.isEmpty()) {
            return toUpdateCount;
        } else {
            while (!this.priorityQueue.isEmpty() && toUpdateCount > 0) {
                toUpdateCount--;
                long i = this.priorityQueue.removeFirstLong();
                int j = Mth.clamp(this.getLevel(i), 0, this.levelCount - 1);
                int k = this.computedLevels.remove(i) & 255;
                if (k < j) {
                    this.setLevel(i, k);
                    this.checkNeighborsAfterUpdate(i, k, true);
                } else if (k > j) {
                    this.setLevel(i, this.levelCount - 1);
                    if (k != this.levelCount - 1) {
                        this.priorityQueue.enqueue(i, this.calculatePriority(this.levelCount - 1, k));
                        this.computedLevels.put(i, (byte)k);
                    }

                    this.checkNeighborsAfterUpdate(i, j, false);
                }
            }

            this.hasWork = !this.priorityQueue.isEmpty();
            return toUpdateCount;
        }
    }

    public int getQueueSize() {
        return this.computedLevels.size();
    }

    protected boolean isSource(long pos) {
        return pos == Long.MAX_VALUE;
    }

    /**
     * Computes level propagated from neighbors of specified position with given existing level, excluding the given source position.
     */
    protected abstract int getComputedLevel(long pos, long excludedSourcePos, int level);

    protected abstract void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing);

    protected abstract int getLevel(long chunkPos);

    protected abstract void setLevel(long chunkPos, int level);

    /**
     * Returns level propagated from start position with specified level to the neighboring end position.
     */
    protected abstract int computeLevelFromNeighbor(long startPos, long endPos, int startLevel);
}
