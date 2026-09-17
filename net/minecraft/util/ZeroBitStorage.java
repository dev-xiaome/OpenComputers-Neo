package net.minecraft.util;

import java.util.Arrays;
import java.util.function.IntConsumer;
import org.apache.commons.lang3.Validate;

public class ZeroBitStorage implements BitStorage {
    public static final long[] RAW = new long[0];
    private final int size;

    public ZeroBitStorage(int size) {
        this.size = size;
    }

    @Override
    public int getAndSet(int index, int value) {
        Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        Validate.inclusiveBetween(0L, 0L, (long)value);
        return 0;
    }

    /**
     * Sets the entry at the given location to the given value
     */
    @Override
    public void set(int index, int value) {
        Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        Validate.inclusiveBetween(0L, 0L, (long)value);
    }

    /**
     * Gets the entry at the given index
     */
    @Override
    public int get(int index) {
        Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        return 0;
    }

    @Override
    public long[] getRaw() {
        return RAW;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getBits() {
        return 0;
    }

    @Override
    public void getAll(IntConsumer consumer) {
        for (int i = 0; i < this.size; i++) {
            consumer.accept(0);
        }
    }

    @Override
    public void unpack(int[] array) {
        Arrays.fill(array, 0, this.size, 0);
    }

    @Override
    public BitStorage copy() {
        return this;
    }
}
