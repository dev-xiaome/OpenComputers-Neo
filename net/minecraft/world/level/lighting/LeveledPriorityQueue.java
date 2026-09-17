package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

public class LeveledPriorityQueue {
    private final int levelCount;
    private final LongLinkedOpenHashSet[] queues;
    private int firstQueuedLevel;

    public LeveledPriorityQueue(int levelCount, final int expectedSize) {
        this.levelCount = levelCount;
        this.queues = new LongLinkedOpenHashSet[levelCount];

        for (int i = 0; i < levelCount; i++) {
            this.queues[i] = new LongLinkedOpenHashSet(expectedSize, 0.5F) {
                @Override
                protected void rehash(int firstQueuedLevel) {
                    if (firstQueuedLevel > expectedSize) {
                        super.rehash(firstQueuedLevel);
                    }
                }
            };
        }

        this.firstQueuedLevel = levelCount;
    }

    public long removeFirstLong() {
        LongLinkedOpenHashSet longlinkedopenhashset = this.queues[this.firstQueuedLevel];
        long i = longlinkedopenhashset.removeFirstLong();
        if (longlinkedopenhashset.isEmpty()) {
            this.checkFirstQueuedLevel(this.levelCount);
        }

        return i;
    }

    public boolean isEmpty() {
        return this.firstQueuedLevel >= this.levelCount;
    }

    public void dequeue(long value, int levelIndex, int endIndex) {
        LongLinkedOpenHashSet longlinkedopenhashset = this.queues[levelIndex];
        longlinkedopenhashset.remove(value);
        if (longlinkedopenhashset.isEmpty() && this.firstQueuedLevel == levelIndex) {
            this.checkFirstQueuedLevel(endIndex);
        }
    }

    public void enqueue(long value, int levelIndex) {
        this.queues[levelIndex].add(value);
        if (this.firstQueuedLevel > levelIndex) {
            this.firstQueuedLevel = levelIndex;
        }
    }

    private void checkFirstQueuedLevel(int endLevelIndex) {
        int i = this.firstQueuedLevel;
        this.firstQueuedLevel = endLevelIndex;

        for (int j = i + 1; j < endLevelIndex; j++) {
            if (!this.queues[j].isEmpty()) {
                this.firstQueuedLevel = j;
                break;
            }
        }
    }
}
