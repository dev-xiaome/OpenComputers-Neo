package net.minecraft.world.level.chunk;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.VisibleForDebug;

/**
 * A representation of a 16x16x16 cube of nibbles (half-bytes).
 */
public class DataLayer {
    public static final int LAYER_COUNT = 16;
    public static final int LAYER_SIZE = 128;
    public static final int SIZE = 2048;
    private static final int NIBBLE_SIZE = 4;
    @Nullable
    protected byte[] data;
    private int defaultValue;

    public DataLayer() {
        this(0);
    }

    public DataLayer(int size) {
        this.defaultValue = size;
    }

    public DataLayer(byte[] data) {
        this.data = data;
        this.defaultValue = 0;
        if (data.length != 2048) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("DataLayer should be 2048 bytes not: " + data.length));
        }
    }

    /**
     * Note all coordinates must be in the range [0, 16), they <strong>are not checked</strong>, and will either silently overrun the array or throw an exception.
     * @return The value of this data layer at the provided position.
     */
    public int get(int x, int y, int z) {
        return this.get(getIndex(x, y, z));
    }

    /**
     * Sets the value of this data layer at the provided position.
     * Note all coordinates must be in the range [0, 16), they <strong>are not checked</strong>, and will either silently overrun the array or throw an exception.
     */
    public void set(int x, int y, int z, int value) {
        this.set(getIndex(x, y, z), value);
    }

    private static int getIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    private int get(int index) {
        if (this.data == null) {
            return this.defaultValue;
        } else {
            int i = getByteIndex(index);
            int j = getNibbleIndex(index);
            return this.data[i] >> 4 * j & 15;
        }
    }

    private void set(int index, int value) {
        byte[] abyte = this.getData();
        int i = getByteIndex(index);
        int j = getNibbleIndex(index);
        int k = ~(15 << 4 * j);
        int l = (value & 15) << 4 * j;
        abyte[i] = (byte)(abyte[i] & k | l);
    }

    private static int getNibbleIndex(int index) {
        return index & 1;
    }

    private static int getByteIndex(int index) {
        return index >> 1;
    }

    public void fill(int defaultValue) {
        this.defaultValue = defaultValue;
        this.data = null;
    }

    private static byte packFilled(int value) {
        byte b0 = (byte)value;

        for (int i = 4; i < 8; i += 4) {
            b0 = (byte)(b0 | value << i);
        }

        return b0;
    }

    public byte[] getData() {
        if (this.data == null) {
            this.data = new byte[2048];
            if (this.defaultValue != 0) {
                Arrays.fill(this.data, packFilled(this.defaultValue));
            }
        }

        return this.data;
    }

    public DataLayer copy() {
        return this.data == null ? new DataLayer(this.defaultValue) : new DataLayer((byte[])this.data.clone());
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = 0; i < 4096; i++) {
            stringbuilder.append(Integer.toHexString(this.get(i)));
            if ((i & 15) == 15) {
                stringbuilder.append("\n");
            }

            if ((i & 0xFF) == 255) {
                stringbuilder.append("\n");
            }
        }

        return stringbuilder.toString();
    }

    @VisibleForDebug
    public String layerToString(int unused) {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = 0; i < 256; i++) {
            stringbuilder.append(Integer.toHexString(this.get(i)));
            if ((i & 15) == 15) {
                stringbuilder.append("\n");
            }
        }

        return stringbuilder.toString();
    }

    public boolean isDefinitelyHomogenous() {
        return this.data == null;
    }

    public boolean isDefinitelyFilledWith(int value) {
        return this.data == null && this.defaultValue == value;
    }

    public boolean isEmpty() {
        return this.data == null && this.defaultValue == 0;
    }
}
