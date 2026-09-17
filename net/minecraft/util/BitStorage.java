package net.minecraft.util;

import java.util.function.IntConsumer;

public interface BitStorage {
    int getAndSet(int index, int value);

    /**
     * Sets the entry at the given location to the given value
     */
    void set(int index, int value);

    /**
     * Gets the entry at the given index
     */
    int get(int index);

    long[] getRaw();

    int getSize();

    int getBits();

    void getAll(IntConsumer consumer);

    void unpack(int[] array);

    BitStorage copy();
}
