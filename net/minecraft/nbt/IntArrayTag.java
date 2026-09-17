package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public class IntArrayTag extends CollectionTag<IntTag> {
    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<IntArrayTag> TYPE = new TagType.VariableSize<IntArrayTag>() {
        public IntArrayTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return new IntArrayTag(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            return visitor.visit(readAccounted(input, accounter));
        }

        private static int[] readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(24L);
            int i = input.readInt();
            accounter.accountBytes(4L, (long)i);
            int[] aint = new int[i];

            for (int j = 0; j < i; j++) {
                aint[j] = input.readInt();
            }

            return aint;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            input.skipBytes(input.readInt() * 4);
        }

        @Override
        public String getName() {
            return "INT[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Int_Array";
        }
    };
    private int[] data;

    public IntArrayTag(int[] data) {
        this.data = data;
    }

    public IntArrayTag(List<Integer> dataList) {
        this(toArray(dataList));
    }

    private static int[] toArray(List<Integer> dataList) {
        int[] aint = new int[dataList.size()];

        for (int i = 0; i < dataList.size(); i++) {
            Integer integer = dataList.get(i);
            aint[i] = integer == null ? 0 : integer;
        }

        return aint;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);

        for (int i : this.data) {
            output.writeInt(i);
        }
    }

    @Override
    public int sizeInBytes() {
        return 24 + 4 * this.data.length;
    }

    @Override
    public byte getId() {
        return 11;
    }

    @Override
    public TagType<IntArrayTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    public IntArrayTag copy() {
        int[] aint = new int[this.data.length];
        System.arraycopy(this.data, 0, aint, 0, this.data.length);
        return new IntArrayTag(aint);
    }

    @Override
    public boolean equals(Object other) {
        return this == other ? true : other instanceof IntArrayTag && Arrays.equals(this.data, ((IntArrayTag)other).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    public int[] getAsIntArray() {
        return this.data;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitIntArray(this);
    }

    @Override
    public int size() {
        return this.data.length;
    }

    public IntTag get(int index) {
        return IntTag.valueOf(this.data[index]);
    }

    public IntTag set(int index, IntTag tag) {
        int i = this.data[index];
        this.data[index] = tag.getAsInt();
        return IntTag.valueOf(i);
    }

    public void add(int index, IntTag tag) {
        this.data = ArrayUtils.add(this.data, index, tag.getAsInt());
    }

    @Override
    public boolean setTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data[index] = ((NumericTag)nbt).getAsInt();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag nbt) {
        if (nbt instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, index, ((NumericTag)nbt).getAsInt());
            return true;
        } else {
            return false;
        }
    }

    public IntTag remove(int index) {
        int i = this.data[index];
        this.data = ArrayUtils.remove(this.data, index);
        return IntTag.valueOf(i);
    }

    @Override
    public byte getElementType() {
        return 3;
    }

    @Override
    public void clear() {
        this.data = new int[0];
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
