package net.minecraft.nbt;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public class LongArrayTag extends CollectionTag<LongTag> {
    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<LongArrayTag> TYPE = new TagType.VariableSize<LongArrayTag>() {
        public LongArrayTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return new LongArrayTag(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static long[] readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(24L);
            int i = input.readInt();
            accounter.accountBytes(8L, (long)i);
            long[] along = new long[i];

            for (int j = 0; j < i; j++) {
                along[j] = input.readLong();
            }

            return along;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            input.skipBytes(input.readInt() * 8);
        }

        @Override
        public String getName() {
            return "LONG[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Long_Array";
        }
    };
    private long[] data;

    public LongArrayTag(long[] data) {
        this.data = data;
    }

    public LongArrayTag(LongSet dataSet) {
        this.data = dataSet.toLongArray();
    }

    public LongArrayTag(List<Long> dataList) {
        this(toArray(dataList));
    }

    private static long[] toArray(List<Long> dataList) {
        long[] along = new long[dataList.size()];

        for (int i = 0; i < dataList.size(); i++) {
            Long olong = dataList.get(i);
            along[i] = olong == null ? 0L : olong;
        }

        return along;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);

        for (long i : this.data) {
            output.writeLong(i);
        }
    }

    @Override
    public int sizeInBytes() {
        return 24 + 8 * this.data.length;
    }

    @Override
    public byte getId() {
        return 12;
    }

    @Override
    public TagType<LongArrayTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    public LongArrayTag copy() {
        long[] along = new long[this.data.length];
        System.arraycopy(this.data, 0, along, 0, this.data.length);
        return new LongArrayTag(along);
    }

    @Override
    public boolean equals(Object other) {
        return this == other ? true : other instanceof LongArrayTag && Arrays.equals(this.data, ((LongArrayTag)other).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitLongArray(this);
    }

    public long[] getAsLongArray() {
        return this.data;
    }

    @Override
    public int size() {
        return this.data.length;
    }

    public LongTag get(int index) {
        return LongTag.valueOf(this.data[index]);
    }

    public LongTag set(int index, LongTag tag) {
        long i = this.data[index];
        this.data[index] = tag.getAsLong();
        return LongTag.valueOf(i);
    }

    public void add(int index, LongTag tag) {
        this.data = ArrayUtils.add(this.data, index, tag.getAsLong());
    }

    @Override
    public boolean setTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data[index] = ((NumericTag)nbt).getAsLong();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, index, ((NumericTag)nbt).getAsLong());
            return true;
        } else {
            return false;
        }
    }

    public LongTag remove(int index) {
        long i = this.data[index];
        this.data = ArrayUtils.remove(this.data, index);
        return LongTag.valueOf(i);
    }

    @Override
    public byte getElementType() {
        return 4;
    }

    @Override
    public void clear() {
        this.data = new long[0];
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
