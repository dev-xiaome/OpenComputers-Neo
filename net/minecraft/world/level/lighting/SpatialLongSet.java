package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.NoSuchElementException;
import net.minecraft.util.Mth;

public class SpatialLongSet extends LongLinkedOpenHashSet {
    private final SpatialLongSet.InternalMap map;

    public SpatialLongSet(int expectedSize, float loadFactor) {
        super(expectedSize, loadFactor);
        this.map = new SpatialLongSet.InternalMap(expectedSize / 64, loadFactor);
    }

    @Override
    public boolean add(long value) {
        return this.map.addBit(value);
    }

    @Override
    public boolean rem(long value) {
        return this.map.removeBit(value);
    }

    @Override
    public long removeFirstLong() {
        return this.map.removeFirstBit();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    protected static class InternalMap extends Long2LongLinkedOpenHashMap {
        private static final int X_BITS = Mth.log2(60000000);
        private static final int Z_BITS = Mth.log2(60000000);
        private static final int Y_BITS = 64 - X_BITS - Z_BITS;
        private static final int Y_OFFSET = 0;
        private static final int Z_OFFSET = Y_BITS;
        private static final int X_OFFSET = Y_BITS + Z_BITS;
        private static final long OUTER_MASK = 3L << X_OFFSET | 3L | 3L << Z_OFFSET;
        private int lastPos = -1;
        private long lastOuterKey;
        private final int minSize;

        public InternalMap(int minSize, float loadFactor) {
            super(minSize, loadFactor);
            this.minSize = minSize;
        }

        static long getOuterKey(long value) {
            return value & ~OUTER_MASK;
        }

        static int getInnerKey(long value) {
            int i = (int)(value >>> X_OFFSET & 3L);
            int j = (int)(value >>> 0 & 3L);
            int k = (int)(value >>> Z_OFFSET & 3L);
            return i << 4 | k << 2 | j;
        }

        static long getFullKey(long value, int trailingZeros) {
            value |= (long)(trailingZeros >>> 4 & 3) << X_OFFSET;
            value |= (long)(trailingZeros >>> 2 & 3) << Z_OFFSET;
            return value | (long)(trailingZeros >>> 0 & 3) << 0;
        }

        public boolean addBit(long value) {
            long i = getOuterKey(value);
            int j = getInnerKey(value);
            long k = 1L << j;
            int l;
            if (i == 0L) {
                if (this.containsNullKey) {
                    return this.replaceBit(this.n, k);
                }

                this.containsNullKey = true;
                l = this.n;
            } else {
                if (this.lastPos != -1 && i == this.lastOuterKey) {
                    return this.replaceBit(this.lastPos, k);
                }

                long[] along = this.key;
                l = (int)HashCommon.mix(i) & this.mask;

                for (long i1 = along[l]; i1 != 0L; i1 = along[l]) {
                    if (i1 == i) {
                        this.lastPos = l;
                        this.lastOuterKey = i;
                        return this.replaceBit(l, k);
                    }

                    l = l + 1 & this.mask;
                }
            }

            this.key[l] = i;
            this.value[l] = k;
            if (this.size == 0) {
                this.first = this.last = l;
                this.link[l] = -1L;
            } else {
                this.link[this.last] = this.link[this.last] ^ (this.link[this.last] ^ (long)l & 4294967295L) & 4294967295L;
                this.link[l] = ((long)this.last & 4294967295L) << 32 | 4294967295L;
                this.last = l;
            }

            if (this.size++ >= this.maxFill) {
                this.rehash(HashCommon.arraySize(this.size + 1, this.f));
            }

            return false;
        }

        private boolean replaceBit(int index, long value) {
            boolean flag = (this.value[index] & value) != 0L;
            this.value[index] = this.value[index] | value;
            return flag;
        }

        public boolean removeBit(long value) {
            long i = getOuterKey(value);
            int j = getInnerKey(value);
            long k = 1L << j;
            if (i == 0L) {
                return this.containsNullKey ? this.removeFromNullEntry(k) : false;
            } else if (this.lastPos != -1 && i == this.lastOuterKey) {
                return this.removeFromEntry(this.lastPos, k);
            } else {
                long[] along = this.key;
                int l = (int)HashCommon.mix(i) & this.mask;

                for (long i1 = along[l]; i1 != 0L; i1 = along[l]) {
                    if (i == i1) {
                        this.lastPos = l;
                        this.lastOuterKey = i;
                        return this.removeFromEntry(l, k);
                    }

                    l = l + 1 & this.mask;
                }

                return false;
            }
        }

        private boolean removeFromNullEntry(long value) {
            if ((this.value[this.n] & value) == 0L) {
                return false;
            } else {
                this.value[this.n] = this.value[this.n] & ~value;
                if (this.value[this.n] != 0L) {
                    return true;
                } else {
                    this.containsNullKey = false;
                    this.size--;
                    this.fixPointers(this.n);
                    if (this.size < this.maxFill / 4 && this.n > 16) {
                        this.rehash(this.n / 2);
                    }

                    return true;
                }
            }
        }

        private boolean removeFromEntry(int index, long value) {
            if ((this.value[index] & value) == 0L) {
                return false;
            } else {
                this.value[index] = this.value[index] & ~value;
                if (this.value[index] != 0L) {
                    return true;
                } else {
                    this.lastPos = -1;
                    this.size--;
                    this.fixPointers(index);
                    this.shiftKeys(index);
                    if (this.size < this.maxFill / 4 && this.n > 16) {
                        this.rehash(this.n / 2);
                    }

                    return true;
                }
            }
        }

        public long removeFirstBit() {
            if (this.size == 0) {
                throw new NoSuchElementException();
            } else {
                int i = this.first;
                long j = this.key[i];
                int k = Long.numberOfTrailingZeros(this.value[i]);
                this.value[i] = this.value[i] & ~(1L << k);
                if (this.value[i] == 0L) {
                    this.removeFirstLong();
                    this.lastPos = -1;
                }

                return getFullKey(j, k);
            }
        }

        @Override
        protected void rehash(int newSize) {
            if (newSize > this.minSize) {
                super.rehash(newSize);
            }
        }
    }
}
