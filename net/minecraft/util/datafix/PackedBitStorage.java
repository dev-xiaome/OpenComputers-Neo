package net.minecraft.util.datafix;

import net.minecraft.util.Mth;
import org.apache.commons.lang3.Validate;

public class PackedBitStorage {
    private static final int BIT_TO_LONG_SHIFT = 6;
    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;

    public PackedBitStorage(int bits, int size) {
        this(bits, size, new long[Mth.roundToward(size * bits, 64) / 64]);
    }

    public PackedBitStorage(int bits, int size, long[] data) {
        Validate.inclusiveBetween(1L, 32L, (long)bits);
        this.size = size;
        this.bits = bits;
        this.data = data;
        this.mask = (1L << bits) - 1L;
        int i = Mth.roundToward(size * bits, 64) / 64;
        if (data.length != i) {
            throw new IllegalArgumentException("Invalid length given for storage, got: " + data.length + " but expected: " + i);
        }
    }

    public void set(int index, int value) {
        Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        Validate.inclusiveBetween(0L, this.mask, (long)value);
        int i = index * this.bits;
        int j = i >> 6;
        int k = (index + 1) * this.bits - 1 >> 6;
        int l = i ^ j << 6;
        this.data[j] = this.data[j] & ~(this.mask << l) | ((long)value & this.mask) << l;
        if (j != k) {
            int i1 = 64 - l;
            int j1 = this.bits - i1;
            this.data[k] = this.data[k] >>> j1 << j1 | ((long)value & this.mask) >> i1;
        }
    }

    public int get(int index) {
        Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        int i = index * this.bits;
        int j = i >> 6;
        int k = (index + 1) * this.bits - 1 >> 6;
        int l = i ^ j << 6;
        if (j == k) {
            return (int)(this.data[j] >>> l & this.mask);
        } else {
            int i1 = 64 - l;
            return (int)((this.data[j] >>> l | this.data[k] << i1) & this.mask);
        }
    }

    public long[] getRaw() {
        return this.data;
    }

    public int getBits() {
        return this.bits;
    }
}
