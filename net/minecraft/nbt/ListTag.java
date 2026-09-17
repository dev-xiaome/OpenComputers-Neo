package net.minecraft.nbt;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ListTag extends CollectionTag<Tag> {
    private static final int SELF_SIZE_IN_BYTES = 37;
    public static final TagType<ListTag> TYPE = new TagType.VariableSize<ListTag>() {
        public ListTag load(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            ListTag listtag;
            try {
                listtag = loadList(input, accounter);
            } finally {
                accounter.popDepth();
            }

            return listtag;
        }

        private static ListTag loadList(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(37L);
            byte b0 = input.readByte();
            int i = input.readInt();
            if (b0 == 0 && i > 0) {
                throw new NbtFormatException("Missing type on ListTag");
            } else {
                accounter.accountBytes(4L, (long)i);
                TagType<?> tagtype = TagTypes.getType(b0);
                List<Tag> list = Lists.newArrayListWithCapacity(i);

                for (int j = 0; j < i; j++) {
                    list.add(tagtype.load(input, accounter));
                }

                return new ListTag(list, b0);
            }
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            StreamTagVisitor.ValueResult streamtagvisitor$valueresult;
            try {
                streamtagvisitor$valueresult = parseList(input, visitor, accounter);
            } finally {
                accounter.popDepth();
            }

            return streamtagvisitor$valueresult;
        }

        private static StreamTagVisitor.ValueResult parseList(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(37L);
            TagType<?> tagtype = TagTypes.getType(input.readByte());
            int i = input.readInt();
            switch (visitor.visitList(tagtype, i)) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    tagtype.skip(input, i, accounter);
                    return visitor.visitContainerEnd();
                default:
                    accounter.accountBytes(4L, (long)i);
                    int j = 0;

                    while (true) {
                        label41: {
                            if (j < i) {
                                switch (visitor.visitElement(tagtype, j)) {
                                    case HALT:
                                        return StreamTagVisitor.ValueResult.HALT;
                                    case BREAK:
                                        tagtype.skip(input, accounter);
                                        break;
                                    case SKIP:
                                        tagtype.skip(input, accounter);
                                        break label41;
                                    default:
                                        switch (tagtype.parse(input, visitor, accounter)) {
                                            case HALT:
                                                return StreamTagVisitor.ValueResult.HALT;
                                            case BREAK:
                                                break;
                                            default:
                                                break label41;
                                        }
                                }
                            }

                            int k = i - 1 - j;
                            if (k > 0) {
                                tagtype.skip(input, k, accounter);
                            }

                            return visitor.visitContainerEnd();
                        }

                        j++;
                    }
            }
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            try {
                TagType<?> tagtype = TagTypes.getType(input.readByte());
                int i = input.readInt();
                tagtype.skip(input, i, accounter);
            } finally {
                accounter.popDepth();
            }
        }

        @Override
        public String getName() {
            return "LIST";
        }

        @Override
        public String getPrettyName() {
            return "TAG_List";
        }
    };
    private final List<Tag> list;
    private byte type;

    ListTag(List<Tag> list, byte type) {
        this.list = list;
        this.type = type;
    }

    public ListTag() {
        this(Lists.newArrayList(), (byte)0);
    }

    /**
     * Neo: create a list tag with the given initial capacity.
     *
     * @param initialCapacity the initial capacity of the list tag
     */
    public ListTag(int initialCapacity) {
        this(new java.util.ArrayList<>(initialCapacity), (byte)0);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        if (this.list.isEmpty()) {
            this.type = 0;
        } else {
            this.type = this.list.get(0).getId();
        }

        output.writeByte(this.type);
        output.writeInt(this.list.size());

        for (Tag tag : this.list) {
            tag.write(output);
        }
    }

    @Override
    public int sizeInBytes() {
        int i = 37;
        i += 4 * this.list.size();

        for (Tag tag : this.list) {
            i += tag.sizeInBytes();
        }

        return i;
    }

    @Override
    public byte getId() {
        return 9;
    }

    @Override
    public TagType<ListTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    private void updateTypeAfterRemove() {
        if (this.list.isEmpty()) {
            this.type = 0;
        }
    }

    @Override
    public Tag remove(int p_128751_) {
        Tag tag = this.list.remove(p_128751_);
        this.updateTypeAfterRemove();
        return tag;
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public CompoundTag getCompound(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 10) {
                return (CompoundTag)tag;
            }
        }

        return new CompoundTag();
    }

    public ListTag getList(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 9) {
                return (ListTag)tag;
            }
        }

        return new ListTag();
    }

    public short getShort(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 2) {
                return ((ShortTag)tag).getAsShort();
            }
        }

        return 0;
    }

    public int getInt(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 3) {
                return ((IntTag)tag).getAsInt();
            }
        }

        return 0;
    }

    public int[] getIntArray(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 11) {
                return ((IntArrayTag)tag).getAsIntArray();
            }
        }

        return new int[0];
    }

    public long[] getLongArray(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 12) {
                return ((LongArrayTag)tag).getAsLongArray();
            }
        }

        return new long[0];
    }

    public double getDouble(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 6) {
                return ((DoubleTag)tag).getAsDouble();
            }
        }

        return 0.0;
    }

    public float getFloat(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            if (tag.getId() == 5) {
                return ((FloatTag)tag).getAsFloat();
            }
        }

        return 0.0F;
    }

    public String getString(int index) {
        if (index >= 0 && index < this.list.size()) {
            Tag tag = this.list.get(index);
            return tag.getId() == 8 ? tag.getAsString() : tag.toString();
        } else {
            return "";
        }
    }

    @Override
    public int size() {
        return this.list.size();
    }

    public Tag get(int index) {
        return this.list.get(index);
    }

    @Override
    public Tag set(int p_128760_, Tag p_128761_) {
        Tag tag = this.get(p_128760_);
        if (!this.setTag(p_128760_, p_128761_)) {
            throw new UnsupportedOperationException(String.format(Locale.ROOT, "Trying to add tag of type %d to list of %d", p_128761_.getId(), this.type));
        } else {
            return tag;
        }
    }

    @Override
    public void add(int p_128753_, Tag p_128754_) {
        if (!this.addTag(p_128753_, p_128754_)) {
            throw new UnsupportedOperationException(String.format(Locale.ROOT, "Trying to add tag of type %d to list of %d", p_128754_.getId(), this.type));
        }
    }

    @Override
    public boolean setTag(int index, Tag nbt) {
        if (this.updateType(nbt)) {
            this.list.set(index, nbt);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag nbt) {
        if (this.updateType(nbt)) {
            this.list.add(index, nbt);
            return true;
        } else {
            return false;
        }
    }

    private boolean updateType(Tag tag) {
        if (tag.getId() == 0) {
            return false;
        } else if (this.type == 0) {
            this.type = tag.getId();
            return true;
        } else {
            return this.type == tag.getId();
        }
    }

    public ListTag copy() {
        Iterable<Tag> iterable = (Iterable<Tag>)(TagTypes.getType(this.type).isValue() ? this.list : Iterables.transform(this.list, Tag::copy));
        List<Tag> list = Lists.newArrayList(iterable);
        return new ListTag(list, this.type);
    }

    @Override
    public boolean equals(Object other) {
        return this == other ? true : other instanceof ListTag && Objects.equals(this.list, ((ListTag)other).list);
    }

    @Override
    public int hashCode() {
        return this.list.hashCode();
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitList(this);
    }

    @Override
    public byte getElementType() {
        return this.type;
    }

    @Override
    public void clear() {
        this.list.clear();
        this.type = 0;
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        switch (visitor.visitList(TagTypes.getType(this.type), this.list.size())) {
            case HALT:
                return StreamTagVisitor.ValueResult.HALT;
            case BREAK:
                return visitor.visitContainerEnd();
            default:
                int i = 0;

                while (i < this.list.size()) {
                    Tag tag = this.list.get(i);
                    switch (visitor.visitElement(tag.getType(), i)) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        default:
                            switch (tag.accept(visitor)) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                        case SKIP:
                            i++;
                    }
                }

                return visitor.visitContainerEnd();
        }
    }
}
