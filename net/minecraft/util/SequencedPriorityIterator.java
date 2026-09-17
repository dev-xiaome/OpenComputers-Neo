package net.minecraft.util;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.Deque;
import javax.annotation.Nullable;

public final class SequencedPriorityIterator<T> extends AbstractIterator<T> {
    private static final int MIN_PRIO = Integer.MIN_VALUE;
    @Nullable
    private Deque<T> highestPrioQueue = null;
    private int highestPrio = Integer.MIN_VALUE;
    private final Int2ObjectMap<Deque<T>> queuesByPriority = new Int2ObjectOpenHashMap<>();

    public void add(T value, int priority) {
        if (priority == this.highestPrio && this.highestPrioQueue != null) {
            this.highestPrioQueue.addLast(value);
        } else {
            Deque<T> deque = this.queuesByPriority.computeIfAbsent(priority, p_308896_ -> Queues.newArrayDeque());
            deque.addLast(value);
            if (priority >= this.highestPrio) {
                this.highestPrioQueue = deque;
                this.highestPrio = priority;
            }
        }
    }

    @Nullable
    @Override
    protected T computeNext() {
        if (this.highestPrioQueue == null) {
            return this.endOfData();
        } else {
            T t = this.highestPrioQueue.removeFirst();
            if (t == null) {
                return this.endOfData();
            } else {
                if (this.highestPrioQueue.isEmpty()) {
                    this.switchCacheToNextHighestPrioQueue();
                }

                return t;
            }
        }
    }

    private void switchCacheToNextHighestPrioQueue() {
        int i = Integer.MIN_VALUE;
        Deque<T> deque = null;

        for (Entry<Deque<T>> entry : Int2ObjectMaps.fastIterable(this.queuesByPriority)) {
            Deque<T> deque1 = entry.getValue();
            int j = entry.getIntKey();
            if (j > i && !deque1.isEmpty()) {
                i = j;
                deque = deque1;
                if (j == this.highestPrio - 1) {
                    break;
                }
            }
        }

        this.highestPrio = i;
        this.highestPrioQueue = deque;
    }
}
