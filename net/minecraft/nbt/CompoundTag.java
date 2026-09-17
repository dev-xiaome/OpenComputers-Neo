package net.minecraft.nbt;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;

public class CompoundTag implements Tag {
    public static final Codec<CompoundTag> CODEC = Codec.PASSTHROUGH
        .comapFlatMap(
            p_311527_ -> {
                Tag tag = p_311527_.convert(NbtOps.INSTANCE).getValue();
                return tag instanceof CompoundTag compoundtag
                    ? DataResult.success(compoundtag == p_311527_.getValue() ? compoundtag.copy() : compoundtag)
                    : DataResult.error(() -> "Not a compound tag: " + tag);
            },
            p_311526_ -> new Dynamic<>(NbtOps.INSTANCE, p_311526_.copy())
        );
    private static final int SELF_SIZE_IN_BYTES = 48;
    private static final int MAP_ENTRY_SIZE_IN_BYTES = 32;
    public static final TagType<CompoundTag> TYPE = new TagType.VariableSize<CompoundTag>() {
        public CompoundTag load(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            CompoundTag compoundtag;
            try {
                compoundtag = loadCompound(input, accounter);
            } finally {
                accounter.popDepth();
            }

            return compoundtag;
        }

        private static byte readNamedTagType(DataInput p_302338_, NbtAccounter p_302362_) throws IOException {
            p_302362_.accountBytes(2);
            return p_302338_.readByte();
        }

        private static CompoundTag loadCompound(DataInput input, NbtAccounter nbtAccounter) throws IOException {
            nbtAccounter.accountBytes(48L);
            Map<String, Tag> map = Maps.newHashMap();

            byte b0;
            while((b0 = readNamedTagType(input, nbtAccounter)) != 0) {
                String s = nbtAccounter.readUTF(input.readUTF());
                nbtAccounter.accountBytes(4); //Forge: 4 extra bytes for the object allocation.
                Tag tag = CompoundTag.readNamedTagData(TagTypes.getType(b0), s, input, nbtAccounter);
                if (map.put(s, tag) == null) {
                    nbtAccounter.accountBytes(36L);
                }
            }

            return new CompoundTag(map);
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            StreamTagVisitor.ValueResult streamtagvisitor$valueresult;
            try {
                streamtagvisitor$valueresult = parseCompound(input, visitor, accounter);
            } finally {
                accounter.popDepth();
            }

            return streamtagvisitor$valueresult;
        }

        private static StreamTagVisitor.ValueResult parseCompound(DataInput input, StreamTagVisitor visitor, NbtAccounter nbtAccounter) throws IOException {
            nbtAccounter.accountBytes(48L);

            byte b0;
            label35:
            while ((b0 = input.readByte()) != 0) {
                TagType<?> tagtype = TagTypes.getType(b0);
                switch (visitor.visitEntry(tagtype)) {
                    case HALT:
                        return StreamTagVisitor.ValueResult.HALT;
                    case BREAK:
                        StringTag.skipString(input);
                        tagtype.skip(input, nbtAccounter);
                        break label35;
                    case SKIP:
                        StringTag.skipString(input);
                        tagtype.skip(input, nbtAccounter);
                        break;
                    default:
                        String s = readString(input, nbtAccounter);
                        switch (visitor.visitEntry(tagtype, s)) {
                            case HALT:
                                return StreamTagVisitor.ValueResult.HALT;
                            case BREAK:
                                tagtype.skip(input, nbtAccounter);
                                break label35;
                            case SKIP:
                                tagtype.skip(input, nbtAccounter);
                                break;
                            default:
                                nbtAccounter.accountBytes(36L);
                                switch (tagtype.parse(input, visitor, nbtAccounter)) {
                                    case HALT:
                                        return StreamTagVisitor.ValueResult.HALT;
                                    case BREAK:
                                }
                        }
                }
            }

            if (b0 != 0) {
                while ((b0 = input.readByte()) != 0) {
                    StringTag.skipString(input);
                    TagTypes.getType(b0).skip(input, nbtAccounter);
                }
            }

            return visitor.visitContainerEnd();
        }

        private static String readString(DataInput input, NbtAccounter accounter) throws IOException {
            String s = input.readUTF();
            accounter.accountBytes(28L);
            accounter.accountBytes(2L, (long)s.length());
            return s;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            byte b0;
            try {
                while ((b0 = input.readByte()) != 0) {
                    StringTag.skipString(input);
                    TagTypes.getType(b0).skip(input, accounter);
                }
            } finally {
                accounter.popDepth();
            }
        }

        @Override
        public String getName() {
            return "COMPOUND";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Compound";
        }
    };
    private final Map<String, Tag> tags;

    protected CompoundTag(Map<String, Tag> tags) {
        this.tags = tags;
    }

    public CompoundTag() {
        this(Maps.newHashMap());
    }

    /**
     * Neo: create a compound tag that is generally suitable to hold the given amount of entries
     * without needing to resize the internal map.
     *
     * @param expectedEntries the expected number of entries that the compound tag will have
     * @see HashMap#newHashMap(int)
     */
    public CompoundTag(int expectedEntries) {
        this(HashMap.newHashMap(expectedEntries));
    }

    @Override
    public void write(DataOutput output) throws IOException {
        for (String s : this.tags.keySet()) {
            Tag tag = this.tags.get(s);
            writeNamedTag(s, tag, output);
        }

        output.writeByte(0);
    }

    @Override
    public int sizeInBytes() {
        int i = 48;

        for (Entry<String, Tag> entry : this.tags.entrySet()) {
            i += 28 + 2 * entry.getKey().length();
            i += 36;
            i += entry.getValue().sizeInBytes();
        }

        return i;
    }

    public Set<String> getAllKeys() {
        return this.tags.keySet();
    }

    @Override
    public byte getId() {
        return 10;
    }

    @Override
    public TagType<CompoundTag> getType() {
        return TYPE;
    }

    public int size() {
        return this.tags.size();
    }

    @Nullable
    public Tag put(String key, Tag value) {
        if (value == null) throw new IllegalArgumentException("Invalid null NBT value with key " + key);
        return this.tags.put(key, value);
    }

    public void putByte(String key, byte value) {
        this.tags.put(key, ByteTag.valueOf(value));
    }

    public void putShort(String key, short value) {
        this.tags.put(key, ShortTag.valueOf(value));
    }

    public void putInt(String key, int value) {
        this.tags.put(key, IntTag.valueOf(value));
    }

    public void putLong(String key, long value) {
        this.tags.put(key, LongTag.valueOf(value));
    }

    public void putUUID(String key, UUID value) {
        this.tags.put(key, NbtUtils.createUUID(value));
    }

    public UUID getUUID(String key) {
        return NbtUtils.loadUUID(this.get(key));
    }

    public boolean hasUUID(String key) {
        Tag tag = this.get(key);
        return tag != null && tag.getType() == IntArrayTag.TYPE && ((IntArrayTag)tag).getAsIntArray().length == 4;
    }

    public void putFloat(String key, float value) {
        this.tags.put(key, FloatTag.valueOf(value));
    }

    public void putDouble(String key, double value) {
        this.tags.put(key, DoubleTag.valueOf(value));
    }

    public void putString(String key, String value) {
        this.tags.put(key, StringTag.valueOf(value));
    }

    public void putByteArray(String key, byte[] value) {
        this.tags.put(key, new ByteArrayTag(value));
    }

    public void putByteArray(String key, List<Byte> value) {
        this.tags.put(key, new ByteArrayTag(value));
    }

    public void putIntArray(String key, int[] value) {
        this.tags.put(key, new IntArrayTag(value));
    }

    public void putIntArray(String key, List<Integer> value) {
        this.tags.put(key, new IntArrayTag(value));
    }

    public void putLongArray(String key, long[] value) {
        this.tags.put(key, new LongArrayTag(value));
    }

    public void putLongArray(String key, List<Long> value) {
        this.tags.put(key, new LongArrayTag(value));
    }

    public void putBoolean(String key, boolean value) {
        this.tags.put(key, ByteTag.valueOf(value));
    }

    @Nullable
    public Tag get(String key) {
        return this.tags.get(key);
    }

    /**
     * Gets the byte identifier of the tag of the specified {@code key}, or {@code 0} if no tag exists for the {@code key}.
     */
    public byte getTagType(String key) {
        Tag tag = this.tags.get(key);
        return tag == null ? 0 : tag.getId();
    }

    public boolean contains(String key) {
        return this.tags.containsKey(key);
    }

    /**
     * Returns whether the tag of the specified {@code key} is a particular {@code tagType}. If the {@code tagType} is {@code 99}, all numeric tags will be checked against the type of the stored tag.
     */
    public boolean contains(String key, int tagType) {
        int i = this.getTagType(key);
        if (i == tagType) {
            return true;
        } else {
            return tagType != 99 ? false : i == 1 || i == 2 || i == 3 || i == 4 || i == 5 || i == 6;
        }
    }

    public byte getByte(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsByte();
            }
        } catch (ClassCastException classcastexception) {
        }

        return 0;
    }

    public short getShort(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsShort();
            }
        } catch (ClassCastException classcastexception) {
        }

        return 0;
    }

    public int getInt(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsInt();
            }
        } catch (ClassCastException classcastexception) {
        }

        return 0;
    }

    public long getLong(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsLong();
            }
        } catch (ClassCastException classcastexception) {
        }

        return 0L;
    }

    public float getFloat(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsFloat();
            }
        } catch (ClassCastException classcastexception) {
        }

        return 0.0F;
    }

    public double getDouble(String key) {
        try {
            if (this.contains(key, 99)) {
                return ((NumericTag)this.tags.get(key)).getAsDouble();
            }
        } catch (ClassCastException classcastexception) {
        }

        return 0.0;
    }

    public String getString(String key) {
        try {
            if (this.contains(key, 8)) {
                return this.tags.get(key).getAsString();
            }
        } catch (ClassCastException classcastexception) {
        }

        return "";
    }

    public byte[] getByteArray(String key) {
        try {
            if (this.contains(key, 7)) {
                return ((ByteArrayTag)this.tags.get(key)).getAsByteArray();
            }
        } catch (ClassCastException classcastexception) {
            throw new ReportedException(this.createReport(key, ByteArrayTag.TYPE, classcastexception));
        }

        return new byte[0];
    }

    public int[] getIntArray(String key) {
        try {
            if (this.contains(key, 11)) {
                return ((IntArrayTag)this.tags.get(key)).getAsIntArray();
            }
        } catch (ClassCastException classcastexception) {
            throw new ReportedException(this.createReport(key, IntArrayTag.TYPE, classcastexception));
        }

        return new int[0];
    }

    public long[] getLongArray(String key) {
        try {
            if (this.contains(key, 12)) {
                return ((LongArrayTag)this.tags.get(key)).getAsLongArray();
            }
        } catch (ClassCastException classcastexception) {
            throw new ReportedException(this.createReport(key, LongArrayTag.TYPE, classcastexception));
        }

        return new long[0];
    }

    public CompoundTag getCompound(String key) {
        try {
            if (this.contains(key, 10)) {
                return (CompoundTag)this.tags.get(key);
            }
        } catch (ClassCastException classcastexception) {
            throw new ReportedException(this.createReport(key, TYPE, classcastexception));
        }

        return new CompoundTag();
    }

    public ListTag getList(String key, int tagType) {
        try {
            if (this.getTagType(key) == 9) {
                ListTag listtag = (ListTag)this.tags.get(key);
                if (!listtag.isEmpty() && listtag.getElementType() != tagType) {
                    return new ListTag();
                }

                return listtag;
            }
        } catch (ClassCastException classcastexception) {
            throw new ReportedException(this.createReport(key, ListTag.TYPE, classcastexception));
        }

        return new ListTag();
    }

    public boolean getBoolean(String key) {
        return this.getByte(key) != 0;
    }

    public void remove(String key) {
        this.tags.remove(key);
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    public boolean isEmpty() {
        return this.tags.isEmpty();
    }

    private CrashReport createReport(String tagName, TagType<?> type, ClassCastException exception) {
        CrashReport crashreport = CrashReport.forThrowable(exception, "Reading NBT data");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Corrupt NBT tag", 1);
        crashreportcategory.setDetail("Tag type found", () -> this.tags.get(tagName).getType().getName());
        crashreportcategory.setDetail("Tag type expected", type::getName);
        crashreportcategory.setDetail("Tag name", tagName);
        return crashreport;
    }

    protected CompoundTag shallowCopy() {
        return new CompoundTag(new HashMap<>(this.tags));
    }

    public CompoundTag copy() {
        Map<String, Tag> map = Maps.newHashMap(Maps.transformValues(this.tags, Tag::copy));
        return new CompoundTag(map);
    }

    @Override
    public boolean equals(Object other) {
        return this == other ? true : other instanceof CompoundTag && Objects.equals(this.tags, ((CompoundTag)other).tags);
    }

    @Override
    public int hashCode() {
        return this.tags.hashCode();
    }

    private static void writeNamedTag(String name, Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF(name);
            tag.write(output);
        }
    }

    static Tag readNamedTagData(TagType<?> type, String name, DataInput input, NbtAccounter accounter) {
        try {
            return type.load(input, accounter);
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("NBT Tag");
            crashreportcategory.setDetail("Tag name", name);
            crashreportcategory.setDetail("Tag type", type.getName());
            throw new ReportedNbtException(crashreport);
        }
    }

    /**
     * Copies all the tags of {@code other} into this tag, then returns itself.
     * @see #copy()
     */
    public CompoundTag merge(CompoundTag other) {
        for (String s : other.tags.keySet()) {
            Tag tag = other.tags.get(s);
            if (tag.getId() == 10) {
                if (this.contains(s, 10)) {
                    CompoundTag compoundtag = this.getCompound(s);
                    compoundtag.merge((CompoundTag)tag);
                } else {
                    this.put(s, tag.copy());
                }
            } else {
                this.put(s, tag.copy());
            }
        }

        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitCompound(this);
    }

    protected Set<Entry<String, Tag>> entrySet() {
        return this.tags.entrySet();
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        for (Entry<String, Tag> entry : this.tags.entrySet()) {
            Tag tag = entry.getValue();
            TagType<?> tagtype = tag.getType();
            StreamTagVisitor.EntryResult streamtagvisitor$entryresult = visitor.visitEntry(tagtype);
            switch (streamtagvisitor$entryresult) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    return visitor.visitContainerEnd();
                case SKIP:
                    break;
                default:
                    streamtagvisitor$entryresult = visitor.visitEntry(tagtype, entry.getKey());
                    switch (streamtagvisitor$entryresult) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        case SKIP:
                            break;
                        default:
                            StreamTagVisitor.ValueResult streamtagvisitor$valueresult = tag.accept(visitor);
                            switch (streamtagvisitor$valueresult) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                    }
            }
        }

        return visitor.visitContainerEnd();
    }
}
