package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;

public class NbtAccounter {
    private static final int MAX_STACK_DEPTH = 512;
    private final long quota;
    private long usage;
    private final int maxDepth;
    private int depth;

    public NbtAccounter(long quota, int maxDepth) {
        this.quota = quota;
        this.maxDepth = maxDepth;
    }

    public static NbtAccounter create(long quota) {
        return new NbtAccounter(quota, 512);
    }

    public static NbtAccounter unlimitedHeap() {
        return new NbtAccounter(Long.MAX_VALUE, 512);
    }

    public void accountBytes(long bytesPerItem, long items) {
        this.accountBytes(bytesPerItem * items);
    }

    /**
     * Adds the bytes to the current number of read bytes. If the number of bytes is greater than the stored quota, an exception will occur.
     * @throws RuntimeException if the number of {@code usage} bytes exceed the number of {@code quota} bytes
     */
    public void accountBytes(long bytes) {
        if (this.usage + bytes > this.quota) {
            throw new NbtAccounterException(
                "Tried to read NBT tag that was too big; tried to allocate: " + this.usage + " + " + bytes + " bytes where max allowed: " + this.quota
            );
        } else {
            this.usage += bytes;
        }
    }

    public void pushDepth() {
        if (this.depth >= this.maxDepth) {
            throw new NbtAccounterException("Tried to read NBT tag with too high complexity, depth > " + this.maxDepth);
        } else {
            this.depth++;
        }
    }

    public void popDepth() {
        if (this.depth <= 0) {
            throw new NbtAccounterException("NBT-Accounter tried to pop stack-depth at top-level");
        } else {
            this.depth--;
        }
    }

    /*
     * UTF8 is not a simple encoding system, each character can be either
     * 1, 2, or 3 bytes. Depending on where it's numerical value falls.
     * We have to count up each character individually to see the true
     * length of the data.
     *
     * Basic concept is that it uses the MSB of each byte as a 'read more' signal.
     * So it has to shift each 7-bit segment.
     *
     * This will accurately count the correct byte length to encode this string, plus the 2 bytes for it's length prefix.
     */
    public String readUTF(String data) {
        accountBytes(2); //Header length
        if (data == null)
            return data;

        int len = data.length();
        int utflen = 0;

        for (int i = 0; i < len; i++) {
             int c = data.charAt(i);
             if ((c >= 0x0001) && (c <= 0x007F)) utflen += 1;
             else if (c > 0x07FF)                     utflen += 3;
             else                                          utflen += 2;
        }
        accountBytes(utflen);

        return data;
    }

    @VisibleForTesting
    public long getUsage() {
        return this.usage;
    }

    @VisibleForTesting
    public int getDepth() {
        return this.depth;
    }
}
